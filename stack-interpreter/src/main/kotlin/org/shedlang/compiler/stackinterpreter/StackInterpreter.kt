package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.formatModuleName
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.*
import java.math.BigInteger

interface World {
    val args: List<String>
    fun writeToStdout(value: String)
}

object NullWorld: World {
    override val args: List<String>
        get() = listOf()

    override fun writeToStdout(value: String) {
    }
}

class RealWorld(override val args: List<String>): World {
    override fun writeToStdout(value: String) {
        print(value)
    }
}

internal sealed class InterpreterValue

internal object InterpreterUnit: InterpreterValue()

internal data class InterpreterBool(val value: Boolean): InterpreterValue()

internal data class InterpreterUnicodeScalar(val value: Int): InterpreterValue()

internal data class InterpreterInt(val value: BigInteger): InterpreterValue()

internal data class InterpreterString(val value: String): InterpreterValue()

internal data class InterpreterStringSlice(val string: String, val startIndex: Int, val endIndex: Int): InterpreterValue()

internal class InterpreterTuple(val elements: List<InterpreterValue>): InterpreterValue()

internal class InterpreterFunction(
    val bodyInstructions: PersistentList<Instruction>,
    val positionalParameters: List<DefineFunction.Parameter>,
    val namedParameters: List<DefineFunction.Parameter>,
    val scopes: PersistentList<ScopeReference>
) : InterpreterValue()

internal class InterpreterBuiltinFunction(
    val func: (InterpreterState, List<InterpreterValue>) -> InterpreterState
): InterpreterValue()

internal class InterpreterBuiltinOperationHandler(
    val func: (InterpreterState, List<InterpreterValue>, Stack<CallFrame>) -> InterpreterState
): InterpreterValue()

internal class InterpreterPartialCall(
    val receiver: InterpreterValue,
    val positionalArguments: List<InterpreterValue>,
    val namedArguments: Map<Identifier, InterpreterValue>
) : InterpreterValue()

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

    fun fieldUpdate(fieldName: Identifier, value: InterpreterValue): InterpreterValue {
        return InterpreterShapeValue(
            tagValue = tagValue,
            fields = fields + mapOf(fieldName to value)
        )
    }
}

internal class InterpreterModule(
    private val fields: Map<Identifier, InterpreterValue>
): InterpreterValue(), InterpreterHasFields {
    override fun field(fieldName: Identifier): InterpreterValue {
        return fields.getValue(fieldName)
    }
}

internal class InterpreterOperation(
    val effect: UserDefinedEffect,
    val operationName: Identifier
): InterpreterValue() {

}

private fun irValueToInterpreterValue(value: IrValue): InterpreterValue {
    return when (value) {
        is IrBool -> InterpreterBool(value.value)
        is IrUnicodeScalar -> InterpreterUnicodeScalar(value.value)
        is IrInt -> InterpreterInt(value.value)
        is IrString -> InterpreterString(value.value)
        is IrTagValue -> InterpreterString(value.value.value.value)
        is IrUnit -> InterpreterUnit
    }
}

internal fun interpreterValueToIrValue(interpreterValue: InterpreterValue): IrValue {
    return when (interpreterValue) {
        is InterpreterBool -> IrBool(interpreterValue.value)
        is InterpreterUnicodeScalar -> IrUnicodeScalar(interpreterValue.value)
        is InterpreterInt -> IrInt(interpreterValue.value)
        is InterpreterString -> IrString(interpreterValue.value)
        is InterpreterUnit -> IrUnit
        else -> throw UnsupportedOperationException()
    }
}

internal fun <T> stackOf(): Stack<T> {
    return Stack(persistentListOf())
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

    fun find(predicate: (T) -> Boolean): T? {
        return stack.findLast(predicate)
    }
}

private var nextScopeId = 1

data class ScopeReference(private val scopeId: Int)

internal fun createScopeReference(): ScopeReference {
    return ScopeReference(nextScopeId++)
}

internal typealias Bindings = PersistentMap<ScopeReference, PersistentMap<Int, InterpreterValue>>

private var nextHandleStateId = 1

data class HandleStateReference(private val handleStateId: Int)

internal fun createHandleStateReference(): HandleStateReference {
    return HandleStateReference(nextHandleStateId++)
}

internal data class EffectHandler(
    val operationHandlers: Map<Identifier, InterpreterValue>,
    val stateReference: HandleStateReference?,
)

internal data class CallFrame(
    private val instructionIndex: Int,
    private val instructions: List<Instruction>,
    private val temporaryStack: Stack<InterpreterValue>,
    private val effectHandlers: Map<Int, EffectHandler>,
    internal val resume: Stack<CallFrame>? = null,
    internal val handleStateReference: HandleStateReference? = null,
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

    fun hasEmptyTemporaryStack(): Boolean {
        return temporaryStack.size == 0
    }

    fun findEffectHandler(effectId: Int): EffectHandler? {
        return effectHandlers[effectId]
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
    // TODO: GC
    private val effectHandleStates: PersistentMap<HandleStateReference, InterpreterValue>,
    private val nativeContext: PersistentMap<ModuleName, Any>,
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

    fun handle(effectId: Int, operationName: Identifier, positionalArguments: List<InterpreterValue>, namedArguments: Map<Identifier, InterpreterValue>): InterpreterState {
        var newCallStack = callStack
        while (true) {
            val effectHandler = newCallStack.last().findEffectHandler(effectId)
            if (effectHandler == null) {
                newCallStack = newCallStack.discard()
            } else {
                val stateReference = effectHandler.stateReference
                val stateArguments = if (stateReference == null) {
                    listOf()
                } else {
                    val state = effectHandleStates[stateReference]!!
                    listOf(state)
                }

                return call(
                    copy(callStack = newCallStack.discard()),
                    receiver = effectHandler.operationHandlers.getValue(operationName),
                    positionalArguments = stateArguments + positionalArguments,
                    namedArguments = namedArguments,
                    resume = callStack,
                    handleStateReference = effectHandler.stateReference,
                )
            }
        }
    }

    fun resume(value: InterpreterValue, newState: InterpreterValue? = null): InterpreterState {
        val currentCallFrame = currentCallFrame()

        val newEffectHandleStates = if (newState == null) {
            effectHandleStates
        } else {
            effectHandleStates.put(currentCallFrame.handleStateReference!!, newState)
        }

        return copy(
            callStack = currentCallFrame.resume!!,
            effectHandleStates = newEffectHandleStates,
        ).pushTemporary(value)
    }

    fun nextInstruction(): InterpreterState {
        return updateCurrentCallFrame { frame -> frame.nextInstruction() }
    }

    fun jump(label: Int): InterpreterState {
        val instructionIndex = instructionIndex(label)
        return updateCurrentCallFrame { frame -> frame.jump(instructionIndex) }
    }

    private fun instructionIndex(label: Int): Int {
        if (!labelToInstructionIndex.containsKey(label)) {
            labelToInstructionIndex[label] = currentCallFrame().findInstructionIndex(label)
        }
        return labelToInstructionIndex.getValue(label)
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

    fun storeNativeContext(moduleName: ModuleName, context: Any): InterpreterState {
        return copy(nativeContext = nativeContext.put(moduleName, context))
    }

    fun loadNativeContext(moduleName: ModuleName): Any? {
        return nativeContext[moduleName]
    }

    fun moduleInitialisation(moduleName: ModuleName): List<Instruction> {
        return image.moduleInitialisation(moduleName)!!
    }

    fun isModuleInitialised(moduleName: ModuleName): Boolean {
        return modules.containsKey(moduleName)
    }

    fun addHandleState(handleStateReference: HandleStateReference, initialHandleState: InterpreterValue): InterpreterState {
        return copy(effectHandleStates = effectHandleStates.put(handleStateReference, initialHandleState))
    }

    fun enterModuleScope(instructions: List<Instruction>): InterpreterState {
        return enter(instructions = instructions, parentScopes = persistentListOf(defaultScope))
    }

    fun enter(
        instructions: List<Instruction>,
        parentScopes: PersistentList<ScopeReference>,
        effectHandlers: Map<Int, EffectHandler> = mapOf(),
        resume: Stack<CallFrame>? = null,
        handleStateReference: HandleStateReference? = null,
    ): InterpreterState {
        val newScope = createScopeReference()
        val frame = CallFrame(
            instructionIndex = 0,
            instructions = instructions,
            scopes = parentScopes.add(newScope),
            temporaryStack = stackOf(),
            effectHandlers = effectHandlers,
            resume = resume,
            handleStateReference = handleStateReference,
        )
        return copy(
            bindings = bindings.put(newScope, persistentMapOf()),
            callStack = callStack.push(frame)
        )
    }

    fun exit(): InterpreterState {
        if (callStack.last().hasEmptyTemporaryStack()) {
            return copy(
                callStack = callStack.discard()
            )
        } else {
            throw Exception("temporary stack not empty")
        }
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

    fun args(): List<String> {
        return world.args
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
        callStack = stackOf(),
        nativeContext = persistentMapOf(),
        modules = loadNativeModules(),
        effectHandleStates = persistentMapOf(),
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

        is UnicodeScalarEquals -> {
            runBinaryUnicodeScalarOperation(initialState) { left, right ->
                InterpreterBool(left == right)
            }
        }

        is UnicodeScalarNotEqual -> {
            runBinaryUnicodeScalarOperation(initialState) { left, right ->
                InterpreterBool(left != right)
            }
        }

        is UnicodeScalarLessThan -> {
            runBinaryUnicodeScalarOperation(initialState) { left, right ->
                InterpreterBool(left < right)
            }
        }

        is UnicodeScalarLessThanOrEqual -> {
            runBinaryUnicodeScalarOperation(initialState) { left, right ->
                InterpreterBool(left <= right)
            }
        }

        is UnicodeScalarGreaterThan -> {
            runBinaryUnicodeScalarOperation(initialState) { left, right ->
                InterpreterBool(left > right)
            }
        }

        is UnicodeScalarGreaterThanOrEqual -> {
            runBinaryUnicodeScalarOperation(initialState) { left, right ->
                InterpreterBool(left >= right)
            }
        }

        is DefineFunction -> {
            val function = toInterpreterFunction(this, scopes = initialState.currentScopes())
            initialState.pushTemporary(function).nextInstruction()
        }

        is DefineShape -> {
            if (fields.any { field -> field.value != null }) {
                throw NotImplementedError()
            }
            val constantFieldValues = persistentMapOf<Identifier, InterpreterValue>()

            val runtimeFields = mapOf(
                Identifier("fields") to InterpreterShapeValue(
                    tagValue = null,
                    fields = fields.associate { field ->
                        field.name to InterpreterShapeValue(
                            tagValue = null,
                            fields = mapOf(
                                Identifier("name") to InterpreterString(field.name.value),
                                Identifier("get") to toInterpreterFunction(
                                    defineShapeFieldGet(
                                        shapeType = AnyType,
                                        fieldName = field.name
                                    ),
                                    scopes = persistentListOf()
                                ),
                                Identifier("update") to toInterpreterFunction(
                                    defineShapeFieldUpdate(
                                        shapeType = AnyType,
                                        fieldName = field.name
                                    ),
                                    scopes = persistentListOf()
                                ),
                            )
                        )
                    }
                ),
                Identifier("name") to InterpreterString(rawShapeType.name.value),
            )

            val value = InterpreterShape(
                tagValue = tagValue,
                constantFieldValues = constantFieldValues,
                fields = runtimeFields
            )

            initialState.pushTemporary(value).nextInstruction()
        }

        is Discard -> {
            initialState.discardTemporary().nextInstruction()
        }

        is Duplicate -> {
            initialState.duplicateTemporary().nextInstruction()
        }

        is EffectDefine -> {
            val effectValue = InterpreterShapeValue(
                tagValue = null,
                fields = effect.operations.keys.associate { operationName ->
                    operationName to InterpreterOperation(effect = effect, operationName = operationName)
                }
            )
            initialState.pushTemporary(effectValue).nextInstruction()
        }

        is EffectHandle -> {
            val (state2, handlerValues) = initialState.popTemporaries(effect.operations.size)
            val (state3, handleStateReference) = if (hasState) {
                val handleStateReference = createHandleStateReference()
                val (state3, value) = state2.popTemporary()
                Pair(
                    state3.addHandleState(handleStateReference, value),
                    handleStateReference,
                )
            } else {
                Pair(state2, null)
            }


            val operationHandlers = effect.operations.keys.sorted().zip(handlerValues).toMap()
            val effectHandler = EffectHandler(operationHandlers = operationHandlers, stateReference = handleStateReference)
            state3
                .nextInstruction()
                .enter(
                    instructions = instructions + listOf(Return),
                    parentScopes = state3.currentScopes(),
                    effectHandlers = mapOf(effect.definitionId to effectHandler),
                )
        }

        is Exit -> {
            val (state2, value) = initialState.popTemporary()
            state2.exit().pushTemporary(value)
        }

        is FieldAccess -> {
            val (state2, receiver) = initialState.popTemporary()
            val module = receiver as InterpreterHasFields
            state2.pushTemporary(module.field(fieldName)).nextInstruction()
        }

        is FieldUpdate -> {
            val (state2, fieldValue) = initialState.popTemporary()
            val (state3, receiver) = state2.popTemporary()
            val shapeValue = receiver as InterpreterShapeValue
            val newShapeValue = shapeValue.fieldUpdate(fieldName, fieldValue)
            state3.pushTemporary(newShapeValue).nextInstruction()
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

        is IntGreaterThan -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterBool(left > right)
            }
        }

        is IntGreaterThanOrEqual -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterBool(left >= right)
            }
        }

        is IntLessThan -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterBool(left < right)
            }
        }

        is IntLessThanOrEqual -> {
            runBinaryIntOperation(initialState) { left, right ->
                InterpreterBool(left <= right)
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

        is JumpEnd -> {
            initialState.jump(destinationLabel)
        }

        is JumpIfFalse -> {
            val (state2, value) = initialState.popTemporary()
            val condition = (value as InterpreterBool).value
            if (condition) {
                state2.nextInstruction()
            } else {
                state2.jump(destinationLabel)
            }
        }

        is JumpIfTrue -> {
            val (state2, value) = initialState.popTemporary()
            val condition = (value as InterpreterBool).value
            if (condition) {
                state2.jump(destinationLabel)
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

        is ModuleInitExit -> {
            initialState.exit()
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

        is ObjectCreate -> {
            initialState.pushTemporary(InterpreterShapeValue(
                tagValue = (objectType as ShapeType).tagValue,
                fields = mapOf(),
            )).nextInstruction()
        }

        is PushValue -> {
            initialState.pushTemporary(irValueToInterpreterValue(value)).nextInstruction()
        }

        is Resume -> {
            val (state2, value) = initialState.popTemporary()
            state2.resume(value)
        }

        is ResumeWithState -> {
            val (state2, newState) = initialState.popTemporary()
            val (state3, value) = state2.popTemporary()
            state3.resume(value, newState)
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

private fun toInterpreterFunction(instruction: DefineFunction, scopes: PersistentList<ScopeReference>): InterpreterFunction {
    return InterpreterFunction(
        bodyInstructions = instruction.bodyInstructions,
        positionalParameters = instruction.positionalParameters,
        namedParameters = instruction.namedParameters,
        scopes = scopes
    )
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

private fun runBinaryUnicodeScalarOperation(
    initialState: InterpreterState,
    func: (left: Int, right: Int) -> InterpreterValue
): InterpreterState {
    val (state2, right) = initialState.popTemporary()
    val (state3, left) = state2.popTemporary()
    val result = func((left as InterpreterUnicodeScalar).value, (right as InterpreterUnicodeScalar).value)
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

internal fun call(
    state: InterpreterState,
    receiver: InterpreterValue,
    positionalArguments: List<InterpreterValue>,
    namedArguments: Map<Identifier, InterpreterValue>,
    resume: Stack<CallFrame>? = null,
    handleStateReference: HandleStateReference? = null,
): InterpreterState {
    return when (receiver) {
        is InterpreterFunction -> {
            val state2 = receiver.positionalParameters.zip(positionalArguments).fold(
                state.enter(
                    instructions = receiver.bodyInstructions,
                    parentScopes = receiver.scopes,
                    resume = resume,
                    handleStateReference = handleStateReference,
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

        is InterpreterBuiltinOperationHandler ->
            receiver.func(state, positionalArguments, resume!!)

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

        is InterpreterOperation -> {
            state.handle(receiver.effect.definitionId, receiver.operationName, positionalArguments, namedArguments)
        }

        else -> throw Exception("cannot call: $receiver")
    }
}

fun executeMain(mainModule: ModuleName, image: Image, world: World): Int {
    val finalState = executeInstructions(
        persistentListOf(
            ModuleInit(mainModule),
            ModuleLoad(mainModule),
            FieldAccess(Identifier("main"), receiverType = AnyType),
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
