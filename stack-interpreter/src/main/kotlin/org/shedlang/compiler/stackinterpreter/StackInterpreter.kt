package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.*
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import java.math.BigInteger

internal interface InterpreterValue

internal data class InterpreterBool(val value: Boolean): InterpreterValue

internal data class InterpreterInt(val value: BigInteger): InterpreterValue

internal class Stack<T>(private val stack: PersistentList<T>) {
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

internal data class CallFrame(
    private val instructionIndex: Int,
    private val instructions: List<Instruction>,
    private val locals: PersistentMap<Int, InterpreterValue>,
    private val temporaryStack: Stack<InterpreterValue>
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

    fun popTemporary(): Pair<CallFrame, InterpreterValue> {
        val (newStack, value) = temporaryStack.pop()
        return Pair(
            copy(temporaryStack = newStack),
            value
        )
    }

    fun storeLocal(variableId: Int, value: InterpreterValue): CallFrame {
        return copy(locals = locals.put(variableId, value))
    }

    fun loadLocal(variableId: Int): InterpreterValue {
        return locals[variableId]!!
    }
}

internal fun createCallFrame(instructions: List<Instruction>): CallFrame {
    return CallFrame(
        instructionIndex = 0,
        instructions = instructions,
        locals = persistentMapOf(),
        temporaryStack = Stack(persistentListOf())
    )
}

internal data class InterpreterState(
    private val globals: PersistentMap<Int, InterpreterValue>,
    private val image: Image,
    private val callStack: Stack<CallFrame>,
    val stdout: String
) {
    fun instruction(): Instruction? {
        return currentCallFrame().currentInstruction()
    }

    fun pushTemporary(value: InterpreterValue): InterpreterState {
        return updateCurrentCallFrame { frame ->
            frame.pushTemporary(value)
        }
    }

    fun popTemporary(): Pair<InterpreterState, InterpreterValue> {
        val (newCallFrame, value) = currentCallFrame().popTemporary()
        return Pair(
            copy(callStack = callStack.discard().push(newCallFrame)),
            value
        )
    }

    fun nextInstruction(): InterpreterState {
        return updateCurrentCallFrame { frame -> frame.nextInstruction() }
    }

    fun relativeJump(size: Int): InterpreterState {
        return updateCurrentCallFrame { frame -> frame.relativeJump(size) }
    }

    fun storeLocal(variableId: Int, value: InterpreterValue): InterpreterState {
        return updateCurrentCallFrame { frame -> frame.storeLocal(variableId, value) }
    }

    fun loadLocal(variableId: Int): InterpreterValue {
        return currentCallFrame().loadLocal(variableId)
    }

    fun storeGlobal(nodeId: Int, value: InterpreterValue): InterpreterState {
        return copy(globals = globals.put(nodeId, value))
    }

    fun loadGlobal(variableId: Int): InterpreterValue {
        return globals[variableId]!!
    }

    fun moduleInitialisation(moduleName: List<Identifier>): List<Instruction> {
        return image.moduleInitialisation(moduleName)
    }

    fun enter(instructions: List<Instruction>): InterpreterState {
        val frame = createCallFrame(instructions = instructions)
        return copy(
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
}

internal fun initialState(image: Image, instructions: List<Instruction>): InterpreterState {
    val frame = createCallFrame(instructions = instructions)
    return InterpreterState(
        globals = persistentMapOf(),
        image = image,
        callStack = Stack(persistentListOf(frame)),
        stdout = ""
    )
}

internal interface Instruction {
    fun run(initialState: InterpreterState): InterpreterState
}


internal class PushValue(private val value: InterpreterValue): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
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

internal val InterpreterIntAdd = BinaryIntOperation { left, right ->
    InterpreterInt(left + right)
}

internal val InterpreterIntSubtract = BinaryIntOperation { left, right ->
    InterpreterInt(left - right)
}

internal val InterpreterIntMultiply = BinaryIntOperation { left, right ->
    InterpreterInt(left * right)
}

internal val InterpreterIntEquals = BinaryIntOperation { left, right ->
    InterpreterBool(left == right)
}

internal class InterpreterFunction(val bodyInstructions: PersistentList<Instruction>) : InterpreterValue

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

internal object Return: Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.exit()
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
        return initialState.nextInstruction().enter(initialState.moduleInitialisation(moduleName))
    }
}

internal class StoreGlobal(private val nodeId: Int, private val value: InterpreterValue): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.storeGlobal(nodeId, value).nextInstruction()
    }
}

internal class LoadGlobal(private val nodeId: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val value = initialState.loadGlobal(nodeId)
        return initialState.pushTemporary(value).nextInstruction()
    }
}

internal class Call(): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, receiver) = initialState.popTemporary()
        val function = receiver as InterpreterFunction
        return initialState.nextInstruction().enter(function.bodyInstructions)
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
    val loader = Loader(references = module.references)
    val instructions = module.node.body.flatMap { statement ->
        loader.loadModuleStatement(statement)
    }.toPersistentList().add(Return)
    return instructions
}

internal class Loader(private val references: ResolvedReferences) {
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
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: CodePointLiteralNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: SymbolNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: TupleNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
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
                    BinaryOperator.ADD -> InterpreterIntAdd
                    BinaryOperator.SUBTRACT -> InterpreterIntSubtract
                    BinaryOperator.MULTIPLY -> InterpreterIntMultiply
                    BinaryOperator.EQUALS -> InterpreterIntEquals
                    else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                }
                return left.addAll(right).add(operation)
            }

            override fun visit(node: IsNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: CallNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: PartialCallNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: FieldAccessNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
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
        return block.statements.flatMap { statement ->
            loadFunctionStatement(statement)
        }.toPersistentList()
    }

    internal fun loadFunctionStatement(statement: FunctionStatementNode): PersistentList<Instruction> {
        return statement.accept(object : FunctionStatementNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: ExpressionStatementNode): PersistentList<Instruction> {
                if (node.type == ExpressionStatementNode.Type.RETURN) {
                    return loadExpression(node.expression)
                } else {
                    throw UnsupportedOperationException("not implemented")
                }
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)
                val target = node.target as TargetNode.Variable
                val store = StoreLocal(target.nodeId)
                return expressionInstructions.add(store)
            }

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }
        })
    }

    internal fun loadModuleStatement(statement: ModuleStatementNode): PersistentList<Instruction> {
        return statement.accept(object : ModuleStatementNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: TypeAliasNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: ShapeNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: UnionNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                val bodyInstructions = loadBlock(node.body)
                val instruction = StoreGlobal(node.nodeId, InterpreterFunction(bodyInstructions))
                return persistentListOf(instruction)
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
            }
        })
    }

    private fun resolveReference(reference: ReferenceNode): Int {
        return references[reference].nodeId
    }
}

internal fun executeInstructions(instructions: PersistentList<Instruction>, image: Image): InterpreterState {
    var state = initialState(image = image, instructions = instructions)

    while (true) {
        val instruction = state.instruction()
        if (instruction == null) {
            return state
        } else {
            state = instruction.run(state)
        }
    }
}
