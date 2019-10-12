package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.*
import java.math.BigInteger

interface InterpreterValue {

}

data class InterpreterBool(val value: Boolean): InterpreterValue

data class InterpreterInt(val value: BigInteger): InterpreterValue

class Stack<T>(private val stack: PersistentList<T>) {
    fun pop(): Pair<Stack<T>, T> {
        val value = stack.last()
        val newStack = Stack(stack.removeAt(stack.lastIndex))
        return Pair(newStack, value)
    }

    fun push(value: T): Stack<T> {
        return Stack(stack.add(value))
    }
}

class Pop(val state: InterpreterState, val value: InterpreterValue) {
    operator fun component1(): InterpreterState {
        return state
    }

    operator fun component2(): InterpreterValue {
        return value
    }
}

data class InterpreterState(
    val instructionIndex: Int,
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
}

fun initialState(): InterpreterState {
    return InterpreterState(
        instructionIndex = 0,
        stack = Stack(persistentListOf())
    )
}

interface InterpreterInstruction {
    fun run(initialState: InterpreterState): InterpreterState
}


class InterpreterPushValue(private val value: InterpreterValue): InterpreterInstruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.push(value).nextInstruction()
    }
}

class InterpreterBinaryIntOperation(
    private val func: (left: BigInteger, right: BigInteger) -> InterpreterValue
): InterpreterInstruction {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, right) = initialState.pop()
        val (state3, left) = state2.pop()
        val result = func((left as InterpreterInt).value, (right as InterpreterInt).value)
        return state3.push(result).nextInstruction()
    }
}

val InterpreterIntAdd = InterpreterBinaryIntOperation { left, right ->
    InterpreterInt(left + right)
}

val InterpreterIntSubtract = InterpreterBinaryIntOperation { left, right ->
    InterpreterInt(left - right)
}

internal fun loadExpression(expression: ExpressionNode): PersistentList<InterpreterInstruction> {
    return expression.accept(object : ExpressionNode.Visitor<PersistentList<InterpreterInstruction>> {
        override fun visit(node: UnitLiteralNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: BooleanLiteralNode): PersistentList<InterpreterInstruction> {
            val push = InterpreterPushValue(InterpreterBool(node.value))
            return persistentListOf(push)
        }

        override fun visit(node: IntegerLiteralNode): PersistentList<InterpreterInstruction> {
            val push = InterpreterPushValue(InterpreterInt(node.value))
            return persistentListOf(push)
        }

        override fun visit(node: StringLiteralNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CodePointLiteralNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: SymbolNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: TupleNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: ReferenceNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: UnaryOperationNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: BinaryOperationNode): PersistentList<InterpreterInstruction> {
            val left = loadExpression(node.left)
            val right = loadExpression(node.right)
            val operation = when (node.operator) {
                BinaryOperator.ADD -> InterpreterIntAdd
                BinaryOperator.SUBTRACT -> InterpreterIntSubtract
                else -> throw UnsupportedOperationException("not implemented")
            }
            return left.addAll(right).add(operation)
        }

        override fun visit(node: IsNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CallNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PartialCallNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionExpressionNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IfNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: WhenNode): PersistentList<InterpreterInstruction> {
            throw UnsupportedOperationException("not implemented")
        }

    })
}
