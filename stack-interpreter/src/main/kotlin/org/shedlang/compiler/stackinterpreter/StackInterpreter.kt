package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.*
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.backends.ModuleCodeInspector
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.StringType
import org.shedlang.compiler.types.Symbol
import java.math.BigInteger

internal interface World {
    fun writeToStdout(value: String)
}

object NullWorld: World {
    override fun writeToStdout(value: String) {
    }
}

internal interface InterpreterValue

internal object InterpreterUnit: InterpreterValue

internal data class InterpreterBool(val value: Boolean): InterpreterValue

internal data class InterpreterInt(val value: BigInteger): InterpreterValue

internal data class InterpreterString(val value: String): InterpreterValue

internal data class InterpreterSymbol(val value: Symbol): InterpreterValue

internal class InterpreterTuple(val elements: List<InterpreterValue>): InterpreterValue

internal class InterpreterFunction(
    val bodyInstructions: PersistentList<Instruction>,
    val parameterIds: List<Int>,
    val scopes: PersistentList<ScopeReference>
) : InterpreterValue

internal class InterpreterBuiltinFunction(
    val func: (InterpreterState, List<InterpreterValue>) -> InterpreterState
): InterpreterValue

internal class InterpreterShape(
    val constantFieldValues: PersistentMap<Identifier, InterpreterValue>
): InterpreterValue

internal interface InterpreterHasFields {
    fun field(fieldName: Identifier): InterpreterValue
}

internal class InterpreterShapeValue(
    private val fields: Map<Identifier, InterpreterValue>
): InterpreterValue, InterpreterHasFields {
    override fun field(fieldName: Identifier): InterpreterValue {
        return fields[fieldName]!!
    }
}

internal class InterpreterModule(
    private val fields: Map<Identifier, InterpreterValue>
): InterpreterValue, InterpreterHasFields {
    override fun field(fieldName: Identifier): InterpreterValue {
        return fields[fieldName]!!
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

internal data class ScopeReference(private val scopeId: Int)

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

    fun loadModule(moduleName: List<Identifier>): InterpreterValue {
        return modules[moduleName]!!
    }

    fun moduleInitialisation(moduleName: List<Identifier>): List<Instruction> {
        return image.moduleInitialisation(moduleName)
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
        modules = persistentMapOf(),
        world = world
    ).enterModuleScope(instructions)
}

internal interface Instruction {
    fun run(initialState: InterpreterState): InterpreterState
}

internal class PushValue(private val value: InterpreterValue): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.pushTemporary(value).nextInstruction()
    }
}

internal object Duplicate: Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.duplicateTemporary().nextInstruction()
    }
}

internal object Discard: Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.discardTemporary().nextInstruction()
    }
}

internal class CreateTuple(private val length: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, elements) = initialState.popTemporaries(length)
        return state2.pushTemporary(InterpreterTuple(elements)).nextInstruction()
    }
}

internal class DeclareFunction(
    val bodyInstructions: PersistentList<Instruction>,
    val parameterIds: List<Int>
): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.pushTemporary(InterpreterFunction(
            bodyInstructions = bodyInstructions,
            parameterIds = parameterIds,
            scopes = initialState.currentScopes()
        )).nextInstruction()
    }
}

internal class DeclareShape(val constantFieldValues: PersistentMap<Identifier, InterpreterValue>) : Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val value = InterpreterShape(constantFieldValues = constantFieldValues)
        return initialState.pushTemporary(value).nextInstruction()
    }
}

internal class BinaryIntOperation(
    private val func: (left: BigInteger, right: BigInteger) -> InterpreterValue
): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, right) = initialState.popTemporary()
        val (state3, left) = state2.popTemporary()
        val result = func((left as InterpreterInt).value, (right as InterpreterInt).value)
        return state3.pushTemporary(result).nextInstruction()
    }
}

internal val IntAdd = BinaryIntOperation { left, right ->
    InterpreterInt(left + right)
}

internal val IntSubtract = BinaryIntOperation { left, right ->
    InterpreterInt(left - right)
}

internal val IntMultiply = BinaryIntOperation { left, right ->
    InterpreterInt(left * right)
}

internal val IntEquals = BinaryIntOperation { left, right ->
    InterpreterBool(left == right)
}

internal class BinaryStringOperation(
    private val func: (left: String, right: String) -> InterpreterValue
): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, right) = initialState.popTemporary()
        val (state3, left) = state2.popTemporary()
        val result = func((left as InterpreterString).value, (right as InterpreterString).value)
        return state3.pushTemporary(result).nextInstruction()
    }
}

internal val StringAdd = BinaryStringOperation { left, right ->
    InterpreterString(left + right)
}

internal class BinarySymbolOperation(
    private val func: (left: Symbol, right: Symbol) -> InterpreterValue
): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, right) = initialState.popTemporary()
        val (state3, left) = state2.popTemporary()
        val result = func((left as InterpreterSymbol).value, (right as InterpreterSymbol).value)
        return state3.pushTemporary(result).nextInstruction()
    }
}

internal val SymbolEquals = BinarySymbolOperation { left, right ->
    InterpreterBool(left == right)
}

internal class TupleAccess(private val elementIndex: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, value) = initialState.popTemporary()
        val element = (value as InterpreterTuple).elements[elementIndex]
        return state2.pushTemporary(element).nextInstruction()
    }
}

internal class FieldAccess(private val fieldName: Identifier): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, receiver) = initialState.popTemporary()
        val module = receiver as InterpreterHasFields
        return state2.pushTemporary(module.field(fieldName)).nextInstruction()
    }
}

internal class RelativeJumpIfFalse(private val size: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, value) = initialState.popTemporary()
        val condition = (value as InterpreterBool).value
        return if (condition) {
            state2.nextInstruction()
        } else {
            state2.relativeJump(size + 1)
        }
    }
}

internal class RelativeJump(private val size: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.relativeJump(size + 1)
    }
}

internal object Exit: Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.exit()
    }
}

internal object Return: Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, value) = initialState.popTemporary()
        return state2.exit().pushTemporary(value)
    }
}

internal class StoreLocal(private val variableId: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, value) = initialState.popTemporary()
        return state2.storeLocal(variableId, value).nextInstruction()
    }
}

internal class LoadLocal(private val variableId: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val value = initialState.loadLocal(variableId)
        return initialState.pushTemporary(value).nextInstruction()
    }
}

internal class InitModule(private val moduleName: List<Identifier>): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.nextInstruction().enterModuleScope(
            instructions = initialState.moduleInitialisation(moduleName)
        )
    }
}

internal class StoreModule(
    private val moduleName: List<Identifier>,
    private val exports: List<Pair<Identifier, Int>>
): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val fields = exports.associate { (name, variableId) ->
            name to initialState.loadLocal(variableId)
        }
        val value = InterpreterModule(fields = fields)
        return initialState.storeModule(moduleName, value).nextInstruction()
    }
}

internal class LoadModule(private val moduleName: List<Identifier>): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.pushTemporary(initialState.loadModule(moduleName)).nextInstruction()
    }
}

internal class Call(private val positionalArgumentCount: Int, private val namedArgumentNames: List<Identifier>): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, namedArgumentValues) = initialState.popTemporaries(namedArgumentNames.size)
        val namedArguments = namedArgumentNames.zip(namedArgumentValues).toMap()
        val (state3, arguments) = state2.popTemporaries(positionalArgumentCount)
        val (state4, receiver) = state3.popTemporary()
        return when (receiver) {
            is InterpreterFunction ->
                receiver.parameterIds.zip(arguments).fold(
                    state4.nextInstruction().enter(
                        instructions = receiver.bodyInstructions,
                        parentScopes = receiver.scopes
                    ),
                    { state, (parameterId, argument) ->
                        state.storeLocal(parameterId, argument)
                    }
                )

            is InterpreterBuiltinFunction ->
                receiver.func(state4.nextInstruction(), arguments)

            is InterpreterShape -> {
                val fieldValues = receiver.constantFieldValues.putAll(namedArguments)
                state4.pushTemporary(InterpreterShapeValue(fields = fieldValues)).nextInstruction()
            }

            else -> throw Exception("cannot call: $receiver")
        }
    }
}

internal class Image(private val modules: Map<List<Identifier>, PersistentList<Instruction>>) {
    companion object {
        val EMPTY = Image(modules = persistentMapOf())
    }

    fun moduleInitialisation(name: List<Identifier>): List<Instruction> {
        return modules[name]!!
    }
}

internal fun loadModuleSet(moduleSet: ModuleSet): Image {
    return Image(moduleSet.modules.associate { module ->
        val instructions = when (module) {
            is Module.Shed -> loadModule(module)
            else -> throw NotImplementedError()
        }
        module.name to instructions
    })
}

private fun loadModule(module: Module.Shed): PersistentList<Instruction> {
    val loader = Loader(
        inspector = ModuleCodeInspector(module),
        references = module.references,
        types = module.types
    )
    return module.node.body
        .flatMap { statement ->
            loader.loadModuleStatement(statement)
        }
        .toPersistentList()
        .add(StoreModule(
            moduleName = module.name,
            exports = module.node.exports.map { export ->
                export.name to module.references[export].nodeId
            }
        ))
        .add(Exit)
}

internal class Loader(
    private val references: ResolvedReferences,
    private val types: Types,
    private val inspector: CodeInspector
) {
    internal fun loadExpression(expression: ExpressionNode): PersistentList<Instruction> {
        return expression.accept(object : ExpressionNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: UnitLiteralNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: BooleanLiteralNode): PersistentList<Instruction> {
                val push = PushValue(InterpreterBool(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: IntegerLiteralNode): PersistentList<Instruction> {
                val push = PushValue(InterpreterInt(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: StringLiteralNode): PersistentList<Instruction> {
                val push = PushValue(InterpreterString(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: CodePointLiteralNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: SymbolNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: TupleNode): PersistentList<Instruction> {
                val elementInstructions = node.elements.flatMap { element -> loadExpression(element) }
                return elementInstructions.toPersistentList().add(CreateTuple(node.elements.size))
            }

            override fun visit(node: ReferenceNode): PersistentList<Instruction> {
                return persistentListOf(LoadLocal(resolveReference(node)))
            }

            override fun visit(node: UnaryOperationNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
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
                    BinaryOperator.SUBTRACT -> IntSubtract
                    BinaryOperator.MULTIPLY -> IntMultiply
                    BinaryOperator.EQUALS -> IntEquals
                    else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                }
                return left.addAll(right).add(operation)
            }

            override fun visit(node: IsNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)

                val discriminator = inspector.discriminatorForIsExpression(node)

                return expressionInstructions
                    .add(FieldAccess(discriminator.fieldName))
                    .add(PushValue(InterpreterSymbol(discriminator.symbolType.symbol)))
                    .add(SymbolEquals)
            }

            override fun visit(node: CallNode): PersistentList<Instruction> {
                val receiverInstructions = loadExpression(node.receiver)
                val argumentInstructions = node.positionalArguments.flatMap { argument ->
                    loadExpression(argument)
                } + node.namedArguments.flatMap { argument ->
                    loadExpression(argument.expression)
                }
                val call = Call(
                    positionalArgumentCount = node.positionalArguments.size,
                    namedArgumentNames = node.namedArguments.map { argument -> argument.name }
                )
                return receiverInstructions.addAll(argumentInstructions).add(call)
            }

            override fun visit(node: PartialCallNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: FieldAccessNode): PersistentList<Instruction> {
                val receiverInstructions = loadExpression(node.receiver)
                val fieldAccess = FieldAccess(fieldName = node.fieldName.identifier)
                return receiverInstructions.add(fieldAccess)
            }

            override fun visit(node: FunctionExpressionNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: IfNode): PersistentList<Instruction> {
                val instructions = mutableListOf<Instruction>()

                val conditionInstructions = node.conditionalBranches.map { branch ->
                    loadExpression(branch.condition)
                }

                val bodyInstructions = node.branchBodies.map { body -> loadBlock(body) }

                node.conditionalBranches.forEachIndexed { branchIndex, _ ->
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
                            (node.conditionalBranches.size - branchIndex - 1) * 2
                    instructions.add(RelativeJump(remainingInstructionCount))
                }

                instructions.addAll(loadBlock(node.elseBranch))
                return instructions.toPersistentList()
            }

            override fun visit(node: WhenNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }
        })
    }

    internal fun loadBlock(block: Block): PersistentList<Instruction> {
        val statementInstructions = block.statements.flatMap { statement ->
            loadFunctionStatement(statement)
        }.toPersistentList()

        return if (blockHasReturnValue(block)) {
            statementInstructions
        } else {
            statementInstructions.add(PushValue(InterpreterUnit))
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
                if (node.type == ExpressionStatementNode.Type.RETURN) {
                    return expressionInstructions
                } else if (node.type == ExpressionStatementNode.Type.NO_RETURN) {
                    return expressionInstructions.add(Discard).add(PushValue(InterpreterUnit))
                } else {
                    throw UnsupportedOperationException("not implemented")
                }
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
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
                return node.members.flatMap { member -> loadShape(member) }.toPersistentList()
            }

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                val bodyInstructions = loadBlock(node.body).add(Return)

                return persistentListOf(
                    DeclareFunction(
                        bodyInstructions = bodyInstructions,
                        parameterIds = node.parameters.map { parameter -> parameter.nodeId }
                    ),
                    StoreLocal(node.nodeId)
                )
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }
        })
    }

    private fun loadShape(node: ShapeBaseNode): PersistentList<Instruction> {
        val constantFieldValues = inspector.shapeFields(node)
            .mapNotNull { field ->
                when (val fieldValue = field.value) {
                    null -> null
                    is FieldValue.Expression -> throw NotImplementedError()
                    is FieldValue.Symbol -> field.name to InterpreterSymbol(fieldValue.symbol)
                }
            }
            .toMap()
            .toPersistentMap()
        return persistentListOf(
            DeclareShape(constantFieldValues = constantFieldValues),
            StoreLocal(node.nodeId)
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
                persistentListOf(StoreLocal(target.nodeId))

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

    private fun resolveReference(reference: ReferenceNode): Int {
        return references[reference].nodeId
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

private object InterpreterBuiltins {
    val intToString = InterpreterBuiltinFunction { state, arguments ->
        val int = (arguments[0] as InterpreterInt).value
        state.pushTemporary(InterpreterString(int.toString()))
    }

    val print = InterpreterBuiltinFunction { state, arguments ->
        val string = (arguments[0] as InterpreterString).value
        state.print(string)
        state.pushTemporary(InterpreterUnit)
    }
}

internal val builtinVariables = mapOf(
    Builtins.intToString.nodeId to InterpreterBuiltins.intToString,
    Builtins.print.nodeId to InterpreterBuiltins.print
)
