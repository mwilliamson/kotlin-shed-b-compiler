package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ast.*

sealed class Expression

abstract class IncompleteExpression: Expression() {
    abstract fun evaluate(context: InterpreterContext): Expression
}

data class VariableReference(val name: String): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        return context.value(name)
    }
}

data class BinaryOperation(
    val operator: Operator,
    val left: Expression,
    val right: Expression
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        return when (left) {
            is IncompleteExpression -> BinaryOperation(
                operator,
                left.evaluate(context),
                right
            )
            is InterpreterValue -> when (right) {
                is IncompleteExpression -> BinaryOperation(
                    operator,
                    left,
                    right.evaluate(context)
                )
                is InterpreterValue ->
                    if (operator == Operator.EQUALS && left is IntegerValue && right is IntegerValue) {
                        BooleanValue(left.value == right.value)
                    } else if (operator == Operator.ADD && left is IntegerValue && right is IntegerValue) {
                        IntegerValue(left.value + right.value)
                    } else if (operator == Operator.SUBTRACT && left is IntegerValue && right is IntegerValue) {
                        IntegerValue(left.value - right.value)
                    } else if (operator == Operator.MULTIPLY && left is IntegerValue && right is IntegerValue) {
                        IntegerValue(left.value * right.value)
                    } else {
                        throw NotImplementedError()
                    }
            }
        }
    }
}

abstract class InterpreterValue: Expression()
object UnitValue: InterpreterValue()
data class BooleanValue(val value: Boolean): InterpreterValue()
data class IntegerValue(val value: Int): InterpreterValue()
data class StringValue(val value: String): InterpreterValue()
data class CharacterValue(val value: Int): InterpreterValue()
data class SymbolValue(val name: String): InterpreterValue()

class InterpreterContext(private val variables: Map<String, InterpreterValue>) {
    fun value(name: String): InterpreterValue {
        return variables[name]!!
    }
}

fun initialise(expression: ExpressionNode): Expression {
    return expression.accept(object : ExpressionNode.Visitor<Expression> {
        override fun visit(node: UnitLiteralNode) = UnitValue
        override fun visit(node: BooleanLiteralNode) = BooleanValue(node.value)
        override fun visit(node: IntegerLiteralNode) = IntegerValue(node.value)
        override fun visit(node: StringLiteralNode) = StringValue(node.value)
        override fun visit(node: CharacterLiteralNode) = CharacterValue(node.value)
        override fun visit(node: SymbolNode) = SymbolValue(node.name)
        override fun visit(node: VariableReferenceNode) = VariableReference(node.name.value)

        override fun visit(node: BinaryOperationNode): Expression
            = BinaryOperation(node.operator, initialise(node.left), initialise(node.right))

        override fun visit(node: IsNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CallNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PartialCallNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionExpressionNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IfNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: WhenNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

fun evaluate(expressionNode: ExpressionNode, context: InterpreterContext): InterpreterValue {
    var expression = initialise(expressionNode)

    while (true) {
        when (expression) {
            is IncompleteExpression ->
                expression = expression.evaluate(context)
            is InterpreterValue ->
                return expression
        }
    }
}
