package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.*
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.backends.ModuleCodeInspector
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.*
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
    val positionalParameterIds: List<Int>,
    val namedParameterIds: List<NamedParameterId>,
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

    fun relativeJump(size: Int): CallFrame {
        return copy(instructionIndex = instructionIndex + size)
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
    private val modules: PersistentMap<List<Identifier>, InterpreterModule>,
    private val world: World
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

    fun relativeJump(size: Int): InterpreterState {
        return updateCurrentCallFrame { frame -> frame.relativeJump(size) }
    }

    fun storeLocal(variableId: Int, value: InterpreterValue): InterpreterState {
        return copy(bindings = currentCallFrame().storeLocal(bindings, variableId, value))
    }

    fun loadLocal(variableId: Int): InterpreterValue {
        return currentCallFrame().loadVariable(bindings, variableId)
    }

    fun storeModule(moduleName: List<Identifier>, value: InterpreterModule): InterpreterState {
        return copy(
            modules = modules.put(moduleName, value)
        )
    }

    fun loadModule(moduleName: List<Identifier>): InterpreterModule {
        val module = modules[moduleName]
        if (module == null) {
            throw Exception("module missing: ${formatModuleName(moduleName)}")
        } else {
            return module
        }
    }

    fun moduleInitialisation(moduleName: List<Identifier>): List<Instruction> {
        return image.moduleInitialisation(moduleName)
    }

    fun isModuleInitialised(moduleName: List<Identifier>): Boolean {
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
        world = world
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

        is CreateTuple -> {
            val (state2, elements) = initialState.popTemporaries(length)
            state2.pushTemporary(InterpreterTuple(elements)).nextInstruction()
        }

        is DeclareFunction -> {
            initialState.pushTemporary(InterpreterFunction(
                bodyInstructions = bodyInstructions,
                positionalParameterIds = positionalParameterIds,
                namedParameterIds = namedParameterIds,
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
                    fields = fields.associate { field ->
                        val getParameterId = freshNodeId()

                        field.name to InterpreterShapeValue(
                            fields = mapOf(
                                Identifier("name") to InterpreterString(field.name.value),
                                Identifier("get") to InterpreterFunction(
                                    bodyInstructions = persistentListOf(
                                        LocalLoad(getParameterId),
                                        FieldAccess(field.name),
                                        Return
                                    ),
                                    positionalParameterIds = listOf(getParameterId),
                                    namedParameterIds = listOf(),
                                    scopes = persistentListOf()
                                )
                            )
                        )
                    }
                )
            )

            val value = InterpreterShape(constantFieldValues = constantFieldValues, fields = runtimeFields)

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

        is RelativeJump -> {
            initialState.relativeJump(size + 1)
        }

        is RelativeJumpIfFalse -> {
            val (state2, value) = initialState.popTemporary()
            val condition = (value as InterpreterBool).value
            if (condition) {
                state2.nextInstruction()
            } else {
                state2.relativeJump(size + 1)
            }
        }

        is RelativeJumpIfTrue -> {
            val (state2, value) = initialState.popTemporary()
            val condition = (value as InterpreterBool).value
            if (condition) {
                state2.relativeJump(size + 1)
            } else {
                state2.nextInstruction()
            }
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

        is TupleAccess -> {
            val (state2, value) = initialState.popTemporary()
            val element = (value as InterpreterTuple).elements[elementIndex]
            state2.pushTemporary(element).nextInstruction()
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
            val state2 = receiver.positionalParameterIds.zip(positionalArguments).fold(
                state.enter(
                    instructions = receiver.bodyInstructions,
                    parentScopes = receiver.scopes
                ),
                { state, (parameterId, argument) ->
                    state.storeLocal(parameterId, argument)
                }
            )
            receiver.namedParameterIds.fold(
                state2,
                { state, namedParameterId ->
                    state.storeLocal(namedParameterId.variableId, namedArguments[namedParameterId.name]!!)
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
            state.pushTemporary(InterpreterShapeValue(fields = fieldValues))
        }

        else -> throw Exception("cannot call: $receiver")
    }
}

class Image internal constructor(private val modules: Map<List<Identifier>, PersistentList<Instruction>>) {
    companion object {
        val EMPTY = Image(modules = persistentMapOf())
    }

    fun moduleInitialisation(name: List<Identifier>): List<Instruction> {
        return modules[name]!!
    }
}

fun loadModuleSet(moduleSet: ModuleSet): Image {
    return Image(moduleSet.modules.filterIsInstance<Module.Shed>().associate { module ->
        module.name to loadModule(module)
    })
}

private fun loadModule(module: Module.Shed): PersistentList<Instruction> {
    val loader = Loader(
        inspector = ModuleCodeInspector(module),
        references = module.references,
        types = module.types
    )
    return loader.loadModule(module)
}

internal class Loader(
    private val references: ResolvedReferences,
    private val types: Types,
    private val inspector: CodeInspector
) {
    internal fun loadModule(module: Module.Shed): PersistentList<Instruction> {
        val moduleNameInstructions = if (isReferenced(module, Builtins.moduleName)) {
            persistentListOf(
                PushValue(IrString(module.name.joinToString(".") { part -> part.value })),
                LocalStore(Builtins.moduleName.nodeId)
            )
        } else {
            persistentListOf()
        }

        val importInstructions = module.node.imports
            .filter { import ->
                import.target.variableBinders().any { variableBinder ->
                    isReferenced(module, variableBinder)
                }
            }
            .flatMap { import ->
                val importedModuleName = resolveImport(module.name, import.path)
                persistentListOf(
                    ModuleInit(importedModuleName),
                    ModuleLoad(importedModuleName)
                ).addAll(loadTarget(import.target))
            }
            .toPersistentList()

        return importInstructions
            .addAll(moduleNameInstructions)
            .addAll(module.node.body.flatMap { statement ->
                loadModuleStatement(statement)
            })
            .add(ModuleStore(
                moduleName = module.name,
                exports = module.node.exports.map { export ->
                    export.name to module.references[export].nodeId
                }
            ))
            .add(Exit)
    }

    private fun isReferenced(module: Module.Shed, variableBinder: VariableBindingNode) =
        module.references.referencedNodes.contains(variableBinder)

    internal fun loadExpression(expression: ExpressionNode): PersistentList<Instruction> {
        return expression.accept(object : ExpressionNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: UnitLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrUnit)
                return persistentListOf(push)
            }

            override fun visit(node: BooleanLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrBool(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: IntegerLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrInt(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: StringLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrString(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: CodePointLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrCodePoint(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: SymbolNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: TupleNode): PersistentList<Instruction> {
                val elementInstructions = node.elements.flatMap { element -> loadExpression(element) }
                return elementInstructions.toPersistentList().add(CreateTuple(node.elements.size))
            }

            override fun visit(node: ReferenceNode): PersistentList<Instruction> {
                return persistentListOf(LocalLoad(resolveReference(node)))
            }

            override fun visit(node: UnaryOperationNode): PersistentList<Instruction> {
                val operandInstructions = loadExpression(node.operand)

                val operationInstruction = when (node.operator) {
                    UnaryOperator.NOT -> BoolNot
                    UnaryOperator.MINUS -> IntMinus
                }

                return operandInstructions.add(operationInstruction)
            }

            override fun visit(node: BinaryOperationNode): PersistentList<Instruction> {
                val left = loadExpression(node.left)
                val right = loadExpression(node.right)
                val operation = when (node.operator) {
                    BinaryOperator.ADD -> when (types.typeOfExpression(node.left)) {
                        IntType -> IntAdd
                        StringType -> StringAdd
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.AND -> when (types.typeOfExpression(node.left)) {
                        BoolType -> {
                            val leftInstructions = loadExpression(node.left)
                            val rightInstructions = loadExpression(node.right)
                            return leftInstructions
                                .add(Duplicate)
                                .add(RelativeJumpIfFalse(rightInstructions.size + 1))
                                .add(Discard)
                                .addAll(rightInstructions)
                        }
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.SUBTRACT -> IntSubtract
                    BinaryOperator.MULTIPLY -> IntMultiply
                    BinaryOperator.EQUALS -> when (types.typeOfExpression(node.left)) {
                        BoolType -> BoolEquals
                        CodePointType -> CodePointEquals
                        IntType -> IntEquals
                        StringType -> StringEquals
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.GREATER_THAN -> when (types.typeOfExpression(node.left)) {
                        CodePointType -> CodePointGreaterThan
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.GREATER_THAN_OR_EQUAL -> when (types.typeOfExpression(node.left)) {
                        CodePointType -> CodePointGreaterThanOrEqual
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.LESS_THAN -> when (types.typeOfExpression(node.left)) {
                        CodePointType -> CodePointLessThan
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.LESS_THAN_OR_EQUAL -> when (types.typeOfExpression(node.left)) {
                        CodePointType -> CodePointLessThanOrEqual
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.NOT_EQUAL -> when (types.typeOfExpression(node.left)) {
                        BoolType -> BoolNotEqual
                        CodePointType -> CodePointNotEqual
                        IntType -> IntNotEqual
                        StringType -> StringNotEqual
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.OR -> when (types.typeOfExpression(node.left)) {
                        BoolType -> {
                            val leftInstructions = loadExpression(node.left)
                            val rightInstructions = loadExpression(node.right)
                            return leftInstructions
                                .add(Duplicate)
                                .add(RelativeJumpIfTrue(rightInstructions.size + 1))
                                .add(Discard)
                                .addAll(rightInstructions)
                        }
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                }
                return left.addAll(right).add(operation)
            }

            override fun visit(node: IsNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)

                val discriminator = inspector.discriminatorForIsExpression(node)

                return expressionInstructions
                    .add(FieldAccess(discriminator.fieldName))
                    .add(PushValue(IrSymbol(discriminator.symbolType.symbol)))
                    .add(SymbolEquals)
            }

            override fun visit(node: CallNode): PersistentList<Instruction> {
                if (inspector.isCast(node)) {
                    val optionsModuleName = listOf(Identifier("Core"), Identifier("Options"))
                    val loadOptionsModuleInstructions = persistentListOf(
                        ModuleInit(optionsModuleName),
                        ModuleLoad(optionsModuleName)
                    )
                    val parameterId = freshNodeId()

                    val failureInstructions = loadOptionsModuleInstructions
                        .add(FieldAccess(Identifier("none")))
                        .add(Return)
                    val successInstructions = loadOptionsModuleInstructions
                        .add(FieldAccess(Identifier("some")))
                        .add(LocalLoad(parameterId))
                        .add(Call(positionalArgumentCount = 1, namedArgumentNames = listOf()))
                        .add(Return)

                    val discriminator = inspector.discriminatorForCast(node)
                    val bodyInstructions = persistentListOf<Instruction>()
                        .add(LocalLoad(parameterId))
                        .add(FieldAccess(discriminator.fieldName))
                        .add(PushValue(IrSymbol(discriminator.symbolType.symbol)))
                        .add(SymbolEquals)
                        .add(RelativeJumpIfFalse(successInstructions.size))
                        .addAll(successInstructions)
                        .addAll(failureInstructions)

                    return persistentListOf(
                        DeclareFunction(
                            positionalParameterIds = listOf(parameterId),
                            bodyInstructions = bodyInstructions,
                            namedParameterIds = listOf()
                        )
                    // TODO: delete if the above works
//                        PushValue(InterpreterFunction(
//                            positionalParameterIds = listOf(parameterId),
//                            bodyInstructions = bodyInstructions,
//                            namedParameterIds = listOf(),
//                            scopes = persistentListOf()
//                        ))
                    )
                } else if (types.typeOfExpression(node.receiver) is VarargsType) {
                    return loadExpression(node.receiver)
                        .addAll(node.positionalArguments.flatMap { argument ->
                            persistentListOf<Instruction>()
                                .add(Duplicate)
                                .add(FieldAccess(Identifier("cons")))
                                .add(Swap)
                                .addAll(loadExpression(argument))
                                .add(Swap)
                        })
                        .add(FieldAccess(Identifier("nil")))
                        .addAll((0 until node.positionalArguments.size).map {
                            Call(
                                positionalArgumentCount = 2,
                                namedArgumentNames = listOf()
                            )
                        })
                } else {
                    val receiverInstructions = loadExpression(node.receiver)
                    val argumentInstructions = loadArguments(node)
                    val call = Call(
                        positionalArgumentCount = node.positionalArguments.size,
                        namedArgumentNames = node.namedArguments.map { argument -> argument.name }
                    )
                    return receiverInstructions.addAll(argumentInstructions).add(call)
                }
            }

            override fun visit(node: PartialCallNode): PersistentList<Instruction> {
                val receiverInstructions = loadExpression(node.receiver)
                val argumentInstructions = loadArguments(node)
                val partialCall = PartialCall(
                    positionalArgumentCount = node.positionalArguments.size,
                    namedArgumentNames = node.namedArguments.map { argument -> argument.name }
                )
                return receiverInstructions.addAll(argumentInstructions).add(partialCall)
            }

            override fun visit(node: FieldAccessNode): PersistentList<Instruction> {
                val receiverInstructions = loadExpression(node.receiver)
                val fieldAccess = FieldAccess(fieldName = node.fieldName.identifier)
                return receiverInstructions.add(fieldAccess)
            }

            override fun visit(node: FunctionExpressionNode): PersistentList<Instruction> {
                return persistentListOf(loadFunctionValue(node))
            }

            override fun visit(node: IfNode): PersistentList<Instruction> {
                val conditionInstructions = node.conditionalBranches.map { branch ->
                    loadExpression(branch.condition)
                }

                return generateBranches(
                    conditionInstructions = conditionInstructions,
                    conditionalBodies = node.conditionalBranches.map { branch -> branch.body },
                    elseBranch = node.elseBranch
                )
            }

            override fun visit(node: WhenNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)

                val conditionInstructions = node.branches.map { branch ->
                    val discriminator = inspector.discriminatorForWhenBranch(node, branch)

                    persistentListOf(
                        Duplicate,
                        FieldAccess(discriminator.fieldName),
                        PushValue(IrSymbol(discriminator.symbolType.symbol)),
                        SymbolEquals
                    )
                }

                return expressionInstructions.addAll(generateBranches(
                    conditionInstructions = conditionInstructions,
                    conditionalBodies = node.branches.map { branch -> branch.body },
                    elseBranch = node.elseBranch
                ))
            }

            private fun generateBranches(
                conditionInstructions: List<PersistentList<Instruction>>,
                conditionalBodies: List<Block>,
                elseBranch: Block?
            ): PersistentList<Instruction> {
                val instructions = mutableListOf<Instruction>()

                val branchBodies = conditionalBodies + elseBranch.nullableToList()
                val bodyInstructions = branchBodies.map { body -> loadBlock(body) }

                conditionInstructions.forEachIndexed { branchIndex, _ ->
                    instructions.addAll(conditionInstructions[branchIndex])
                    instructions.add(RelativeJumpIfFalse(bodyInstructions[branchIndex].size + 1))
                    instructions.addAll(bodyInstructions[branchIndex])

                    val remainingConditionInstructionCount = conditionInstructions.drop(branchIndex + 1)
                        .fold(0) { total, instructions -> total + instructions.size }
                    val remainingBodyInstructionCount = bodyInstructions.drop(branchIndex + 1)
                        .fold(0) { total, instructions -> total + instructions.size }
                    val remainingInstructionCount =
                        remainingConditionInstructionCount +
                            remainingBodyInstructionCount +
                            (conditionInstructions.size - branchIndex - 1) * 2
                    instructions.add(RelativeJump(remainingInstructionCount))
                }

                if (elseBranch != null) {
                    instructions.addAll(loadBlock(elseBranch))
                }
                return instructions.toPersistentList()
            }
        })
    }

    private fun loadArguments(node: CallBaseNode): List<Instruction> {
        return node.positionalArguments.flatMap { argument ->
            loadExpression(argument)
        } + node.namedArguments.flatMap { argument ->
            loadExpression(argument.expression)
        }
    }

    internal fun loadBlock(block: Block): PersistentList<Instruction> {
        val statementInstructions = block.statements.flatMap { statement ->
            loadFunctionStatement(statement)
        }.toPersistentList()

        return if (blockHasReturnValue(block)) {
            statementInstructions
        } else {
            statementInstructions.add(PushValue(IrUnit))
        }
    }

    private fun blockHasReturnValue(block: Block): Boolean {
        val last = block.statements.lastOrNull()
        return last != null && last is ExpressionStatementNode && last.isReturn
    }

    internal fun loadFunctionStatement(statement: FunctionStatementNode): PersistentList<Instruction> {
        return statement.accept(object : FunctionStatementNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: ExpressionStatementNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)
                if (node.type == ExpressionStatementNode.Type.RETURN || node.type == ExpressionStatementNode.Type.TAILREC_RETURN) {
                    return expressionInstructions
                } else if (node.type == ExpressionStatementNode.Type.NO_RETURN) {
                    return expressionInstructions.add(Discard).add(PushValue(IrUnit))
                } else {
                    throw UnsupportedOperationException("not implemented")
                }
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                return loadFunctionDeclaration(node)
            }
        })
    }

    internal fun loadModuleStatement(statement: ModuleStatementNode): PersistentList<Instruction> {
        return statement.accept(object : ModuleStatementNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: TypeAliasNode): PersistentList<Instruction> {
                return persistentListOf()
            }

            override fun visit(node: ShapeNode): PersistentList<Instruction> {
                return loadShape(node)
            }

            override fun visit(node: UnionNode): PersistentList<Instruction> {
                val unionInstructions = persistentListOf(
                    PushValue(IrUnit),
                    LocalStore(node.nodeId)
                )
                val memberInstructions = node.members.flatMap { member -> loadShape(member) }

                return unionInstructions.addAll(memberInstructions)
            }

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                return loadFunctionDeclaration(node)
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }

            override fun visit(node: VarargsDeclarationNode): PersistentList<Instruction> {
                return loadVarargsDeclaration(node)
            }
        })
    }

    private fun loadFunctionDeclaration(node: FunctionDeclarationNode): PersistentList<Instruction> {
        return persistentListOf(
            loadFunctionValue(node),
            LocalStore(node.nodeId)
        )
    }

    private fun loadFunctionValue(node: FunctionNode): DeclareFunction {
        val bodyInstructions = loadBlock(node.body).add(Return)
        return DeclareFunction(
            bodyInstructions = bodyInstructions,
            positionalParameterIds = node.parameters.map { parameter -> parameter.nodeId },
            namedParameterIds = node.namedParameters.map { parameter ->
                NamedParameterId(parameter.name, parameter.nodeId)
            }
        )
    }

    private fun loadShape(node: ShapeBaseNode): PersistentList<Instruction> {
        val shapeFields = inspector.shapeFields(node)

        return persistentListOf(
            DeclareShape(shapeFields),
            LocalStore(node.nodeId)
        )
    }

    private fun loadVal(node: ValNode): PersistentList<Instruction> {
        val expressionInstructions = loadExpression(node.expression)
        val targetInstructions = loadTarget(node.target)
        return expressionInstructions.addAll(targetInstructions)
    }

    private fun loadTarget(target: TargetNode): PersistentList<Instruction> {
        return when (target) {
            is TargetNode.Variable ->
                persistentListOf(LocalStore(target.nodeId))

            is TargetNode.Fields ->
                target.fields.flatMap { (fieldName, fieldTarget) ->
                    persistentListOf(
                        Duplicate,
                        FieldAccess(fieldName = fieldName.identifier)
                    ).addAll(loadTarget(fieldTarget))
                }.toPersistentList().add(Discard)

            is TargetNode.Tuple ->
                target.elements.mapIndexed { elementIndex, target ->
                    persistentListOf(
                        Duplicate,
                        TupleAccess(elementIndex = elementIndex)
                    ).addAll(loadTarget(target))
                }.flatten().toPersistentList().add(Discard)
        }
    }

    private fun loadVarargsDeclaration(node: VarargsDeclarationNode): PersistentList<Instruction> {
        val consInstructions = loadExpression(node.cons)
        val nilInstructions = loadExpression(node.nil)

        return consInstructions.addAll(nilInstructions).add(DeclareVarargs).add(LocalStore(node.nodeId))
    }

    private fun resolveReference(reference: ReferenceNode): Int {
        return references[reference].nodeId
    }
}

fun executeMain(mainModule: List<Identifier>, image: Image, world: World): Int {
    val finalState = executeInstructions(
        persistentListOf(
            ModuleInit(mainModule),
            ModuleLoad(mainModule),
            FieldAccess(Identifier("main")),
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
    instructions: PersistentList<Instruction>,
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
