package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.*
import java.math.BigInteger

interface InterpreterValue {

}

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

class InterpreterState(private val stack: Stack<InterpreterValue>) {
    fun push(value: InterpreterValue): InterpreterState {
        return InterpreterState(stack.push(value))
    }

    fun pop(): Pop {
        val (newStack, value) = stack.pop()
        return Pop(
            state = InterpreterState(stack = newStack),
            value = value
        )
    }
}

fun initialState(): InterpreterState {
    return InterpreterState(
        stack = Stack(persistentListOf())
    )
}

interface InterpreterCommand {
    fun run(initialState: InterpreterState): InterpreterState
}


class InterpreterPushValue(val value: InterpreterValue): InterpreterCommand {
    override fun run(initialState: InterpreterState): InterpreterState {
        return initialState.push(value)
    }
}

object InterpreterIntAdd: InterpreterCommand {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, right) = initialState.pop()
        val (state3, left) = state2.pop()
        val result = InterpreterInt((left as InterpreterInt).value + (right as InterpreterInt).value)
        return state3.push(result)
    }
}

object InterpreterIntSubtract: InterpreterCommand {
    override fun run(initialState: InterpreterState): InterpreterState {
        val (state2, right) = initialState.pop()
        val (state3, left) = state2.pop()
        val result = InterpreterInt((left as InterpreterInt).value - (right as InterpreterInt).value)
        return state3.push(result)
    }
}

internal fun loadExpression(expression: ExpressionNode): PersistentList<InterpreterCommand> {
    return expression.accept(object : ExpressionNode.Visitor<PersistentList<InterpreterCommand>> {
        override fun visit(node: UnitLiteralNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: BooleanLiteralNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IntegerLiteralNode): PersistentList<InterpreterCommand> {
            val push = InterpreterPushValue(InterpreterInt(node.value))
            return persistentListOf(push)
        }

        override fun visit(node: StringLiteralNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CodePointLiteralNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: SymbolNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: TupleNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: ReferenceNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: UnaryOperationNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: BinaryOperationNode): PersistentList<InterpreterCommand> {
            val left = loadExpression(node.left)
            val right = loadExpression(node.right)
            val operation = when (node.operator) {
                BinaryOperator.ADD -> InterpreterIntAdd
                BinaryOperator.SUBTRACT -> InterpreterIntSubtract
                else -> throw UnsupportedOperationException("not implemented")
            }
            return left.addAll(right).add(operation)
        }

        override fun visit(node: IsNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CallNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PartialCallNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionExpressionNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IfNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: WhenNode): PersistentList<InterpreterCommand> {
            throw UnsupportedOperationException("not implemented")
        }

    })
}
