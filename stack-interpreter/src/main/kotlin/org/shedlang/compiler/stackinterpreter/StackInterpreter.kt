package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.formatModuleName
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.Symbol
import org.shedlang.compiler.types.TagValue
import java.math.BigInteger

interface World {
    fun writeToStdout(value: String)
}

object NullWorld: World {
    override fun writeToStdout(value: String) {
    }
}

object RealWorld: World {
    override fun writeToStdout(value: String) {
        print(value)
    }
}

internal sealed class InterpreterValue

internal object InterpreterUnit: InterpreterValue()

internal data class InterpreterBool(val value: Boolean): InterpreterValue()

internal data class InterpreterCodePoint(val value: Int): InterpreterValue()

internal data class InterpreterInt(val value: BigInteger): InterpreterValue()

internal data class InterpreterString(val value: String): InterpreterValue()

internal data class InterpreterStringSlice(val string: String, val startIndex: Int, val endIndex: Int): InterpreterValue()

internal data class InterpreterSymbol(val value: Symbol): InterpreterValue()

internal class InterpreterTuple(val elements: List<InterpreterValue>): InterpreterValue()

internal class InterpreterFunction(
    val bodyInstructions: PersistentList<Instruction>,
    val positionalParameters: List<DeclareFunction.Parameter>,
    val namedParameters: List<DeclareFunction.Parameter>,
    val scopes: PersistentList<ScopeReference>
) : InterpreterValue()

internal class InterpreterBuiltinFunction(
    val func: (InterpreterState, List<InterpreterValue>) -> InterpreterState
): InterpreterValue()

internal class InterpreterPartialCall(
    val receiver: InterpreterValue,
    val positionalArguments: List<InterpreterValue>,
    val namedArguments: Map<Identifier, InterpreterValue>
) : InterpreterValue()

internal class InterpreterVarargs(
    val cons: InterpreterValue,
    val nil: InterpreterValue
): InterpreterValue(), InterpreterHasFields {
    override fun field(fieldName: Identifier): InterpreterValue {
        return when (fieldName.value) {
            "cons" -> cons
            "nil" -> nil
            else -> throw UnsupportedOperationException("no such field: ${fieldName.value}")
        }
    }
}

internal class InterpreterShape(
    val tagValue: TagValue?,
    val constantFieldValues: PersistentMap<Identifier, InterpreterValue>,
    val fields: Map<Identifier, InterpreterValue>
): InterpreterValue(), InterpreterHasFields {
    override fun field(fieldName: Identifier): InterpreterValue {
        return fields.getValue(fieldName)
    }
}

internal interface InterpreterHasFields {
    fun field(fieldName: Identifier): InterpreterValue
}

internal class InterpreterShapeValue(
    val tagValue: TagValue?,
    private val fields: Map<Identifier, InterpreterValue>
): InterpreterValue(), InterpreterHasFields {
    override fun field(fieldName: Identifier): InterpreterValue {
        return fields.getValue(fieldName)
    }
}

internal class InterpreterModule(
    private val fields: Map<Identifier, InterpreterValue>
): InterpreterValue(), InterpreterHasFields {
    override fun field(fieldName: Identifier): InterpreterValue {
        return fields.getValue(fieldName)
    }
}

private fun irValueToInterpreterValue(value: IrValue): InterpreterValue {
    return when (value) {
        is IrBool -> InterpreterBool(value.value)
        is IrCodePoint -> InterpreterCodePoint(value.value)
        is IrInt -> InterpreterInt(value.value)
        is IrString -> InterpreterString(value.value)
        is IrSymbol -> InterpreterSymbol(value.value)
        is IrTagValue -> InterpreterString(value.value.value.value)
        is IrUnit -> InterpreterUnit
    }
}

internal class Stack<T>(private val stack: PersistentList<T>) {
    val size: Int
        get() = stack.size

    fun last(): T {
        return stack.last()
    }

    fun pop(): Pair<Stack<T>, T> {
        val value = stack.last()
        val newStack = discard()
        return Pair(newStack, value)
    }

    fun discard(): Stack<T> {
        return Stack(stack.removeAt(stack.lastIndex))
    }

    fun push(value: T): Stack<T> {
        return Stack(stack.add(value))
    }

    fun replace(func: (T) -> T): Stack<T> {
        val value = stack.last()
        return Stack(stack.removeAt(stack.lastIndex).add(func(value)))
    }
}

private var nextScopeId = 1

data class ScopeReference(private val scopeId: Int)

internal fun createScopeReference(): ScopeReference {
    return ScopeReference(nextScopeId++)
}

internal typealias Bindings = PersistentMap<ScopeReference, PersistentMap<Int, InterpreterValue>>

internal data class CallFrame(
    private val instructionIndex: Int,
    private val instructions: List<Instruction>,
    private val temporaryStack: Stack<InterpreterValue>,
    internal val scopes: PersistentList<ScopeReference>
) {
    fun currentInstruction(): Instruction? {
        return if (instructionIndex < instructions.size) {
            instructions[instructionIndex]
        } else {
            null
        }
    }

    fun nextInstruction(): CallFrame {
        return copy(instructionIndex = instructionIndex + 1)
    }

    fun jump(instructionIndex: Int): CallFrame {
        return copy(instructionIndex = instructionIndex)
    }

    fun findInstructionIndex(label: Int): Int {
        return instructions.indexOfFirst { instruction ->
            instruction is Label && instruction.value == label
        }
    }

    fun pushTemporary(value: InterpreterValue): CallFrame {
        return copy(temporaryStack = temporaryStack.push(value))
    }

    fun duplicateTemporary(): CallFrame {
        return pushTemporary(temporaryStack.last())
    }

    fun popTemporary(): Pair<CallFrame, InterpreterValue> {
        val (newStack, value) = temporaryStack.pop()
        return Pair(
            copy(temporaryStack = newStack),
            value
        )
    }

    fun discardTemporary(): CallFrame {
        return copy(temporaryStack = temporaryStack.discard())
    }

    fun storeLocal(bindings: Bindings, variableId: Int, value: InterpreterValue): Bindings {
        val scope = scopes.last()
        return bindings.put(
            scope,
            bindings[scope]!!.put(variableId, value)
        )
    }

    fun loadVariable(bindings: Bindings, variableId: Int): InterpreterValue {
        for (scope in scopes.asReversed()) {
            val value = bindings[scope]!!.get(variableId)
            if (value != null) {
                return value
            }
        }
        throw Exception("variable not bound: $variableId")
    }
}

internal data class InterpreterState(
    private val bindings: Bindings,
    private val defaultScope: ScopeReference,
    private val image: Image,
    private val callStack: Stack<CallFrame>,
    private val modules: PersistentMap<ModuleName, InterpreterModule>,
    private val world: World,
    private val labelToInstructionIndex: MutableMap<Int, Int>
) {
    fun instruction(): Instruction? {
        val instruction = currentCallFrame().currentInstruction()
        if (instruction != null || callStack.size == 1) {
            return instruction
        } else {
            throw Exception("Stuck!")
        }
    }

    fun pushTemporary(value: InterpreterValue): InterpreterState {
        return updateCurrentCallFrame { frame ->
            frame.pushTemporary(value)
        }
    }

    fun duplicateTemporary(): InterpreterState {
        return updateCurrentCallFrame { frame ->
            frame.duplicateTemporary()
        }
    }

    fun popTemporaries(count: Int): Pair<InterpreterState, List<InterpreterValue>> {
        var state: InterpreterState = this
        val arguments = mutableListOf<InterpreterValue>()
        repeat(count) {
            val (state2, argument) = state.popTemporary()
            arguments.add(argument)
            state = state2
        }
        return Pair(state, arguments.reversed())
    }

    fun popTemporary(): Pair<InterpreterState, InterpreterValue> {
        val (newCallFrame, value) = currentCallFrame().popTemporary()
        return Pair(
            copy(callStack = callStack.discard().push(newCallFrame)),
            value
        )
    }

    fun discardTemporary(): InterpreterState {
        return updateCurrentCallFrame { frame -> frame.discardTemporary() }
    }

    fun nextInstruction(): InterpreterState {
        return updateCurrentCallFrame { frame -> frame.nextInstruction() }
    }

    fun jump(label: Int): InterpreterState {
        if (!labelToInstructionIndex.containsKey(label)) {
            labelToInstructionIndex[label] = currentCallFrame().findInstructionIndex(label)
        }
        val instructionIndex = labelToInstructionIndex.getValue(label)
        return updateCurrentCallFrame { frame -> frame.jump(instructionIndex) }
    }

    fun storeLocal(variableId: Int, value: InterpreterValue): InterpreterState {
        return copy(bindings = currentCallFrame().storeLocal(bindings, variableId, value))
    }

    fun loadLocal(variableId: Int): InterpreterValue {
        return currentCallFrame().loadVariable(bindings, variableId)
    }

    fun storeModule(moduleName: ModuleName, value: InterpreterModule): InterpreterState {
        return copy(
            modules = modules.put(moduleName, value)
        )
    }

    fun loadModule(moduleName: ModuleName): InterpreterModule {
        val module = modules[moduleName]
        if (module == null) {
            throw Exception("module missing: ${formatModuleName(moduleName)}")
        } else {
            return module
        }
    }

    fun moduleInitialisation(moduleName: ModuleName): List<Instruction> {
        return image.moduleInitialisation(moduleName)
    }

    fun isModuleInitialised(moduleName: ModuleName): Boolean {
        return modules.containsKey(moduleName)
    }

    fun enterModuleScope(instructions: List<Instruction>): InterpreterState {
        return enter(instructions = instructions, parentScopes = persistentListOf(defaultScope))
    }

    fun enter(instructions: List<Instruction>, parentScopes: PersistentList<ScopeReference>): InterpreterState {
        val newScope = createScopeReference()
        val frame = CallFrame(
            instructionIndex = 0,
            instructions = instructions,
            scopes = parentScopes.add(newScope),
            temporaryStack = Stack(persistentListOf())
        )
        return copy(
            bindings = bindings.put(newScope, persistentMapOf()),
            callStack = callStack.push(frame)
        )
    }

    fun exit(): InterpreterState {
        return copy(
            callStack = callStack.discard()
        )
    }

    private fun currentCallFrame(): CallFrame {
        return callStack.last()
    }

    private fun updateCurrentCallFrame(update: (CallFrame) -> CallFrame): InterpreterState {
        return copy(callStack = callStack.replace(update))
    }

    fun currentScopes(): PersistentList<ScopeReference> {
        return currentCallFrame().scopes
    }

    fun print(string: String) {
        world.writeToStdout(string)
    }
}

internal fun initialState(
    image: Image,
    instructions: List<Instruction>,
    defaultVariables: Map<Int, InterpreterValue>,
    world: World
): InterpreterState {
    val defaultScope = createScopeReference()
    return InterpreterState(
        bindings = persistentMapOf(defaultScope to defaultVariables.toPersistentMap()),
        defaultScope = defaultScope,
        image = image,
        callStack = Stack(persistentListOf()),
        modules = loadNativeModules(),
        world = world,
        labelToInstructionIndex = mutableMapOf()
    ).enterModuleScope(instructions)
}

internal fun Instruction.run(initialState: InterpreterState): InterpreterState {
    return when (this) {
        is BoolEquals -> {
            runBinaryBoolOperation(initialState) { left, right ->
                InterpreterBool(left == right)
            }
        }

        is BoolNotEqual -> {
            runBinaryBoolOperation(initialState) { left, right ->
                InterpreterBool(left != right)
            }
        }

        is BoolNot -> {
            val (state2, operand) = initialState.popTemporary()
            val result = InterpreterBool(!(operand as InterpreterBool).value)
            state2.pushTemporary(result).nextInstruction()
        }

        is Call -> {
            val (state2, namedArgumentValues) = initialState.popTemporaries(namedArgumentNames.size)
            val namedArguments = namedArgumentNames.zip(namedArgumentValues).toMap()
            val (state3, positionalArguments) = state2.popTemporaries(positionalArgumentCount)
            val (state4, receiver) = state3.popTemporary()
            call(
                state = state4.nextInstruction(),
                receiver = receiver,
                positionalArguments =  positionalArguments,
                namedArguments =  namedArguments
            )
        }

        is PartialCall -> {
            val (state2, namedArgumentValues) = initialState.popTemporaries(namedArgumentNames.size)
            val namedArguments = namedArgumentNames.zip(namedArgumentValues).toMap()
            val (state3, positionalArguments) = state2.popTemporaries(positionalArgumentCount)
            val (state4, receiver) = state3.popTemporary()
            state4.pushTemporary(InterpreterPartialCall(
                receiver = receiver,
                positionalArguments = positionalArguments,
                namedArguments = namedArguments
            )).nextInstruction()
        }

        is CodePointEquals -> {
            runBinaryCodePointOperation(initialState) { left, right ->
                InterpreterBool(left == right)
            }
        }

        is CodePointNotEqual -> {
            runBinaryCodePointOperation(initialState) { left, right ->
                InterpreterBool(left != right)
            }
        }

        is CodePointLessThan -> {
            runBinaryCodePointOperation(initialState) { left, right ->
                InterpreterBool(left < right)
            }
        }

        is CodePointLessThanOrEqual -> {
            runBinaryCodePointOperation(initialState) { left, right ->
                InterpreterBool(left <= right)
            }
        }

        is CodePointGreaterThan -> {
            runBinaryCodePointOperation(initialState) { left, right ->
                InterpreterBool(left > right)
            }
        }

        is CodePointGreaterThanOrEqual -> {
            runBinaryCodePointOperation(initialState) { left, right ->
                InterpreterBool(left >= right)
            }
        }

        is DeclareFunction -> {
            initialState.pushTemporary(InterpreterFunction(
                bodyInstructions = bodyInstructions,
                positionalParameters = positionalParameters,
                namedParameters = namedParameters,
                scopes = initialState.currentScopes()
            )).nextInstruction()
        }

        is DeclareShape -> {
            val constantFieldValues = fields
                .mapNotNull { field ->
                    when (val fieldValue = field.value) {
                        null -> null
                        is FieldValue.Expression -> throw NotImplementedError()
                        is FieldValue.Symbol -> field.name to InterpreterSymbol(fieldValue.symbol)
                    }
                }
                .toMap()
                .toPersistentMap()

            val runtimeFields = mapOf(
                Identifier("fields") to InterpreterShapeValue(
                    tagValue = null,
                    fields = fields.associate { field ->
                        val getParameter = DeclareFunction.Parameter(Identifier("receiver"), freshNodeId())

                        field.name to InterpreterShapeValue(
                            tagValue = null,
                            fields = mapOf(
                                Identifier("name") to InterpreterString(field.name.value),
                                Identifier("get") to InterpreterFunction(
                                    bodyInstructions = persistentListOf(
                                        LocalLoad(getParameter),
                                        FieldAccess(field.name, receiverType = null),
                                        Return
                                    ),
                                    positionalParameters = listOf(getParameter),
                                    namedParameters = listOf(),
                                    scopes = persistentListOf()
                                )
                            )
                        )
                    }
                )
            )

            val value = InterpreterShape(
                tagValue = tagValue,
                constantFieldValues = constantFieldValues,
                fields = runtimeFields
            )

            initialState.pushTemporary(value).nextInstruction()
        }

        is DeclareVarargs -> {
            val (state2, arguments) = initialState.popTemporaries(2)
            val cons = arguments[0]
            val nil = arguments[1]
            state2.pushTemporary(InterpreterVarargs(cons = cons, nil = nil)).nextInstruction()
        }

        is Discard -> {
            initialState.discardTemporary().nextInstruction()
        }

        is Duplicate -> {
            initialState.duplicateTemporary().nextInstruction()
        }

        is Exit -> {
            initialState.exit()
        }

        is FieldAccess -> {
            val (state2, receiver) = initialState.popTemporary()
            val module = receiver as InterpreterHasFields
            state2.pushTemporary(module.field(fieldName)).nextInstruction()
        }

        is IntAdd -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterInt(left + right)
            }
        }

        is IntEquals -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterBool(left == right)
            }
        }

        is IntMinus -> {
            val (state2, operand) = initialState.popTemporary()
            val result = InterpreterInt(-(operand as InterpreterInt).value)
            state2.pushTemporary(result).nextInstruction()
        }

        is IntMultiply -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterInt(left * right)
            }
        }

        is IntNotEqual -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterBool(left != right)
            }
        }

        is IntSubtract -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterInt(left - right)
            }
        }

        is Jump -> {
            initialState.jump(label)
        }

        is JumpIfFalse -> {
            val (state2, value) = initialState.popTemporary()
            val condition = (value as InterpreterBool).value
            if (condition) {
                state2.nextInstruction()
            } else {
                state2.jump(label)
            }
        }

        is JumpIfTrue -> {
            val (state2, value) = initialState.popTemporary()
            val condition = (value as InterpreterBool).value
            if (condition) {
                state2.jump(label)
            } else {
                state2.nextInstruction()
            }
        }

        is Label -> {
            initialState.nextInstruction()
        }

        is LocalLoad -> {
            val value = initialState.loadLocal(variableId)
            initialState.pushTemporary(value).nextInstruction()
        }

        is LocalStore -> {
            val (state2, value) = initialState.popTemporary()
            state2.storeLocal(variableId, value).nextInstruction()
        }

        is ModuleInit -> {
            val state2 = initialState.nextInstruction()

            if (initialState.isModuleInitialised(moduleName)) {
                state2
            } else {
                state2.enterModuleScope(
                    instructions = initialState.moduleInitialisation(moduleName)
                )
            }
        }

        is ModuleLoad -> {
            initialState.pushTemporary(initialState.loadModule(moduleName)).nextInstruction()
        }

        is ModuleStore -> {
            val fields = exports.associate { (name, variableId) ->
                name to initialState.loadLocal(variableId)
            }
            val value = InterpreterModule(fields = fields)
            return initialState.storeModule(moduleName, value).nextInstruction()
        }

        is PushValue -> {
            initialState.pushTemporary(irValueToInterpreterValue(value)).nextInstruction()
        }

        is Return -> {
            val (state2, value) = initialState.popTemporary()
            state2.exit().pushTemporary(value)
        }

        is StringAdd -> {
            runBinaryStringOperation(initialState) { left, right ->
                InterpreterString(left + right)
            }
        }

        is StringEquals -> {
            runBinaryStringOperation(initialState) { left, right ->
                InterpreterBool(left == right)
            }
        }

        is StringNotEqual -> {
            runBinaryStringOperation(initialState) { left, right ->
                InterpreterBool(left != right)
            }
        }

        is Swap -> {
            val (state2, value1) = initialState.popTemporary()
            val (state3, value2) = state2.popTemporary()
            state3.pushTemporary(value1).pushTemporary(value2).nextInstruction()
        }

        is SymbolEquals -> {
            runBinarySymbolOperation(initialState) { left, right ->
                InterpreterBool(left == right)
            }
        }

        is TagValueAccess -> {
            val (state2, value) = initialState.popTemporary()
            val tagValue = (value as InterpreterShapeValue).tagValue!!
            state2.pushTemporary(InterpreterString(tagValue.value.value)).nextInstruction()
        }

        is TagValueEquals -> {
            runBinaryStringOperation(initialState) { left, right ->
                InterpreterBool(left == right)
            }
        }

        is TupleAccess -> {
            val (state2, value) = initialState.popTemporary()
            val element = (value as InterpreterTuple).elements[elementIndex]
            state2.pushTemporary(element).nextInstruction()
        }

        is TupleCreate -> {
            val (state2, elements) = initialState.popTemporaries(length)
            state2.pushTemporary(InterpreterTuple(elements)).nextInstruction()
        }
    }
}

private fun runBinaryBoolOperation(
    initialState: InterpreterState,
    func: (left: Boolean, right: Boolean) -> InterpreterValue
): InterpreterState {
    val (state2, right) = initialState.popTemporary()
    val (state3, left) = state2.popTemporary()
    val result = func((left as InterpreterBool).value, (right as InterpreterBool).value)
    return state3.pushTemporary(result).nextInstruction()
}

private fun runBinaryCodePointOperation(
    initialState: InterpreterState,
    func: (left: Int, right: Int) -> InterpreterValue
): InterpreterState {
    val (state2, right) = initialState.popTemporary()
    val (state3, left) = state2.popTemporary()
    val result = func((left as InterpreterCodePoint).value, (right as InterpreterCodePoint).value)
    return state3.pushTemporary(result).nextInstruction()
}

private fun runBinaryIntOperation(
    initialState: InterpreterState,
    func: (left: BigInteger, right: BigInteger) -> InterpreterValue
): InterpreterState {
    val (state2, right) = initialState.popTemporary()
    val (state3, left) = state2.popTemporary()
    val result = func((left as InterpreterInt).value, (right as InterpreterInt).value)
    return state3.pushTemporary(result).nextInstruction()
}

private fun runBinaryStringOperation(
    initialState: InterpreterState,
    func: (left: String, right: String) -> InterpreterValue
): InterpreterState {
    val (state2, right) = initialState.popTemporary()
    val (state3, left) = state2.popTemporary()
    val result = func((left as InterpreterString).value, (right as InterpreterString).value)
    return state3.pushTemporary(result).nextInstruction()
}

private fun runBinarySymbolOperation(
    initialState: InterpreterState,
    func: (left: Symbol, right: Symbol) -> InterpreterValue
): InterpreterState {
    val (state2, right) = initialState.popTemporary()
    val (state3, left) = state2.popTemporary()
    val result = func((left as InterpreterSymbol).value, (right as InterpreterSymbol).value)
    return state3.pushTemporary(result).nextInstruction()
}

internal fun call(
    state: InterpreterState,
    receiver: InterpreterValue,
    positionalArguments: List<InterpreterValue>,
    namedArguments: Map<Identifier, InterpreterValue>
): InterpreterState {
    return when (receiver) {
        is InterpreterFunction -> {
            val state2 = receiver.positionalParameters.zip(positionalArguments).fold(
                state.enter(
                    instructions = receiver.bodyInstructions,
                    parentScopes = receiver.scopes
                ),
                { state, (parameter, argument) ->
                    state.storeLocal(parameter.variableId, argument)
                }
            )
            receiver.namedParameters.fold(
                state2,
                { state, namedParameter ->
                    state.storeLocal(namedParameter.variableId, namedArguments[namedParameter.name]!!)
                }
            )
        }

        is InterpreterBuiltinFunction ->
            receiver.func(state, positionalArguments)

        is InterpreterPartialCall ->
            call(
                state = state,
                receiver = receiver.receiver,
                positionalArguments = receiver.positionalArguments + positionalArguments,
                namedArguments = receiver.namedArguments + namedArguments
            )

        is InterpreterShape -> {
            val fieldValues = receiver.constantFieldValues.putAll(namedArguments)
            state.pushTemporary(InterpreterShapeValue(tagValue = receiver.tagValue, fields = fieldValues))
        }

        else -> throw Exception("cannot call: $receiver")
    }
}

fun executeMain(mainModule: ModuleName, image: Image, world: World): Int {
    val finalState = executeInstructions(
        persistentListOf(
            ModuleInit(mainModule),
            ModuleLoad(mainModule),
            FieldAccess(Identifier("main"), receiverType = null),
            Call(positionalArgumentCount = 0, namedArgumentNames = listOf())
        ),
        image = image,
        defaultVariables = mapOf(),
        world = world
    )
    val finalValue = finalState.popTemporary().second
    return when (finalValue) {
        is InterpreterInt -> finalValue.value.toInt()
        is InterpreterUnit -> 0
        else -> throw Exception("final value was: $finalValue")
    }
}

internal fun executeInstructions(
    instructions: List<Instruction>,
    image: Image,
    defaultVariables: Map<Int, InterpreterValue>,
    world: World
): InterpreterState {
    var state = initialState(
        image = image,
        instructions = instructions,
        defaultVariables = defaultVariables,
        world = world
    )

    while (true) {
        val instruction = state.instruction()
        if (instruction == null) {
            return state
        } else {
            state = instruction.run(state)
        }
    }
}
