package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.*
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import java.math.BigInteger

internal interface InterpreterValue {

}

internal data class InterpreterBool(val value: Boolean): InterpreterValue

internal data class InterpreterInt(val value: BigInteger): InterpreterValue

internal class Stack<T>(private val stack: PersistentList<T>) {
    fun pop(): Pair<Stack<T>, T> {
        val value = stack.last()
        val newStack = Stack(stack.removeAt(stack.lastIndex))
        return Pair(newStack, value)
    }

    fun push(value: T): Stack<T> {
        return Stack(stack.add(value))
    }
}

internal class Pop(val state: InterpreterState, val value: InterpreterValue) {
    operator fun component1(): InterpreterState {
        return state
    }

    operator fun component2(): InterpreterValue {
        return value
    }
}

internal data class InterpreterState(
    val instructionIndex: Int,
    private val locals: PersistentMap<Int, InterpreterValue>,
    private val stack: Stack<InterpreterValue>
) {
    fun push(value: InterpreterValue): InterpreterState {
        return copy(stack = stack.push(value))
    }

    fun pop(): Pop {
        val (newStack, value) = stack.pop()
        return Pop(
            state = copy(stack = newStack),
            value = value
        )
    }

    fun nextInstruction(): InterpreterState {
        return copy(instructionIndex = instructionIndex + 1)
    }

    fun relativeJump(size: Int): InterpreterState {
        return copy(instructionIndex = instructionIndex + size)
    }

    fun storeLocal(variableId: Int, value: InterpreterValue): InterpreterState {
        return copy(locals = locals.put(variableId, value))
    }

    fun loadLocal(variableId: Int): InterpreterValue {
        return locals[variableId]!!
    }
}

internal fun initialState(): InterpreterState {
    return InterpreterState(
        instructionIndex = 0,
        locals = persistentMapOf(),
        stack = Stack(persistentListOf())
    )
}

internal interface Instruction {
    fun run(initialState: InterpreterState): InterpreterState
}


internal class PushValue(private val value: InterpreterValue): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.push(value).nextInstruction()
    }
}

internal class BinaryIntOperation(
    private val func: (left: BigInteger, right: BigInteger) -> InterpreterValue
): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, right) = initialState.pop()
        val (state3, left) = state2.pop()
        val result = func((left as InterpreterInt).value, (right as InterpreterInt).value)
        return state3.push(result).nextInstruction()
    }
}

internal val InterpreterIntAdd = BinaryIntOperation { left, right ->
    InterpreterInt(left + right)
}

internal val InterpreterIntSubtract = BinaryIntOperation { left, right ->
    InterpreterInt(left - right)
}

internal class RelativeJumpIfFalse(private val size: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, value) = initialState.pop()
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

internal class StoreLocal(private val variableId: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, value) = initialState.pop()
        return state2.storeLocal(variableId, value).nextInstruction()
    }
}

internal class LoadLocal(private val variableId: Int): Instruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val value = initialState.loadLocal(variableId)
        return initialState.push(value).nextInstruction()
    }
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
                    else -> throw UnsupportedOperationException("not implemented")
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
        return block.statements.flatMap { statement -> loadStatement(statement) }.toPersistentList()
    }

    internal fun loadStatement(statement: FunctionStatementNode): PersistentList<Instruction> {
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

    private fun resolveReference(reference: ReferenceNode): Int {
        return references[reference].nodeId
    }
}
