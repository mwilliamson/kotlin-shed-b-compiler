package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ast.*

sealed class Expression {
    class Incomplete(val expression: IncompleteExpression): Expression()
    class Value(val value: InterpreterValue): Expression()
}

interface IncompleteExpression {
    fun evaluate(context: InterpreterContext): Expression
}

data class VariableReference(val name: String): IncompleteExpression {
    override fun evaluate(context: InterpreterContext): Expression {
        return Expression.Value(context.value(name))
    }
}

interface InterpreterValue
object UnitValue: InterpreterValue
data class BooleanValue(val value: Boolean): InterpreterValue
data class IntegerValue(val value: Int): InterpreterValue
data class StringValue(val value: String): InterpreterValue
data class CharacterValue(val value: Int): InterpreterValue
data class SymbolValue(val name: String): InterpreterValue

class InterpreterContext(private val variables: Map<String, InterpreterValue>) {
    fun value(name: String): InterpreterValue {
        return variables[name]!!
    }
}

fun initialise(expression: ExpressionNode): Expression {
    return expression.accept(object : ExpressionNode.Visitor<Expression> {
        override fun visit(node: UnitLiteralNode) = Expression.Value(UnitValue)
        override fun visit(node: BooleanLiteralNode) = Expression.Value(BooleanValue(node.value))
        override fun visit(node: IntegerLiteralNode) = Expression.Value(IntegerValue(node.value))
        override fun visit(node: StringLiteralNode) = Expression.Value(StringValue(node.value))
        override fun visit(node: CharacterLiteralNode) = Expression.Value(CharacterValue(node.value))
        override fun visit(node: SymbolNode) = Expression.Value(SymbolValue(node.name))
        override fun visit(node: VariableReferenceNode) = Expression.Incomplete(VariableReference(node.name.value))

        override fun visit(node: BinaryOperationNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

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
            is Expression.Incomplete ->
                expression = expression.expression.evaluate(context)
            is Expression.Value ->
                return expression.value
        }
    }
}
