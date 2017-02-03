package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.SubExpressionSerialiser
import org.shedlang.compiler.backends.serialiseCStringLiteral

private val INDENTATION_WIDTH = 4

internal fun serialise(node: StatementNode, indentation: Int): String {
    fun line(text: String) = " ".repeat(indentation * INDENTATION_WIDTH) + text + "\n"

    fun simpleStatement(text: String) = line(text + ";")

    return node.accept(object : StatementNode.Visitor<String> {
        override fun visit(node: ReturnNode): String {
            return simpleStatement("return " + serialise(node.expression))
        }

        override fun visit(node: IfStatementNode): String {
            val condition = line("if (" + serialise(node.condition) + ") {")
            val trueBranch = serialiseBlock(node.trueBranch, indentation)
            val falseBranch = if (node.falseBranch.isEmpty()) {
                line("}")
            } else {
                line("} else {") + serialiseBlock(node.falseBranch, indentation) + line("}")
            }
            return condition + trueBranch + falseBranch
        }

        override fun visit(node: ExpressionStatementNode): String {
            return simpleStatement(serialise(node.expression))
        }
    })
}

private fun serialiseBlock(
    statements: List<StatementNode>,
    indentation: Int
): String {
    return statements.map({ statement -> serialise(statement, indentation + 1) }).joinToString("")
}

internal fun serialise(node: ExpressionNode) : String {
    return node.accept(object : ExpressionNode.Visitor<String> {
        override fun visit(node: BooleanLiteralNode): String {
            return if (node.value) "true" else "false"
        }

        override fun visit(node: IntegerLiteralNode): String {
            return node.value.toString()
        }

        override fun visit(node: StringLiteralNode): String {
            return serialiseCStringLiteral(node.value)
        }

        override fun visit(node: VariableReferenceNode): String {
            return node.name
        }

        override fun visit(node: BinaryOperationNode): String {
            return serialiseSubExpression(node, node.left, associative = true) +
                " " +
                serialise(node.operator) +
                " " +
                serialiseSubExpression(node, node.right, associative = false)
        }

        override fun visit(node: FunctionCallNode): String {
            return serialiseSubExpression(node, node.function, associative = true) +
                "(" +
                node.arguments.map(::serialise).joinToString(", ") +
                ")"
        }

    })
}

val subExpressionSerialiser = SubExpressionSerialiser<ExpressionNode>(
    serialise = ::serialise,
    precedence = ::precedence
)

private fun serialiseSubExpression(
    parentNode: ExpressionNode,
    node: ExpressionNode,
    associative: Boolean
): String {
    return subExpressionSerialiser.serialiseSubExpression(
        parentNode = parentNode,
        node = node,
        associative = associative
    )
}

private fun serialise(operator: Operator) = when(operator) {
    Operator.EQUALS -> "==="
    Operator.ADD -> "+"
    Operator.SUBTRACT -> "-"
    Operator.MULTIPLY -> "*"
}

private fun precedence(node: ExpressionNode): Int {
    return node.accept(object : ExpressionNode.Visitor<Int> {
        override fun visit(node: BooleanLiteralNode): Int {
            return 21
        }

        override fun visit(node: IntegerLiteralNode): Int {
            return 21
        }

        override fun visit(node: StringLiteralNode): Int {
            return 21
        }

        override fun visit(node: VariableReferenceNode): Int {
            return 21
        }

        override fun visit(node: BinaryOperationNode): Int {
            return when(node.operator) {
                Operator.EQUALS -> 10
                Operator.ADD -> 13
                Operator.SUBTRACT -> 13
                Operator.MULTIPLY -> 14
            }
        }

        override fun visit(node: FunctionCallNode): Int {
            return 18
        }
    })
}
