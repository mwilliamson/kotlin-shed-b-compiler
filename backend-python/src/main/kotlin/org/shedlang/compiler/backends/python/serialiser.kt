package org.shedlang.compiler.backends.python

import org.shedlang.compiler.backends.python.ast.*

private val INDENTATION_WIDTH = 4

fun serialise(node: PythonStatementNode, indentation: Int = 0): String {
    val statement = node.accept(object : PythonStatementNode.Visitor<String> {
        override fun visit(node: PythonFunctionNode): String {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonExpressionStatementNode): String {
            return serialise(node.expression)
        }

        override fun visit(node: PythonReturnNode): String {
            return "return " + serialise(node.expression)
        }

        override fun visit(node: PythonIfStatementNode): String {
            val condition = "if " + serialise(node.condition) + ":\n"
            val trueBranch = serialiseBlock(node, node.trueBranch, indentation)
            val falseBranch = if (node.falseBranch.isEmpty()) {
                ""
            } else {
                "else:\n" + serialiseBlock(node, node.falseBranch, indentation)
            }
            return condition + trueBranch + falseBranch
        }

        override fun visit(node: PythonPassNode): String {
            return "pass"
        }
    })

    val indentedStatement = " ".repeat(indentation * INDENTATION_WIDTH) + statement
    return ensureEndsWith(indentedStatement, "\n")
}

private fun ensureEndsWith(value: String, suffix: String): String {
    if (value.endsWith(suffix)) {
        return value
    } else {
        return value + suffix
    }
}

private fun serialiseBlock(
    parent: PythonNode,
    statements: List<PythonStatementNode>,
    indentation: Int
): String {
    return if (statements.isEmpty()) {
        listOf(PythonPassNode(source = parent.source))
    } else {
        statements
    }.map({ statement -> serialise(statement, indentation + 1) }).joinToString("")
}

fun serialise(node: PythonExpressionNode): String {
    return node.accept(object : PythonExpressionNode.Visitor<String>{
        override fun visit(node: PythonBooleanLiteralNode): String {
            return if (node.value) "True" else "False"
        }

        override fun visit(node: PythonIntegerLiteralNode): String {
            return node.value.toString()
        }

        override fun visit(node: PythonVariableReferenceNode): String {
            return node.name
        }

        override fun visit(node: PythonBinaryOperationNode): String {
            return serialiseSubExpression(node, node.left, associative = true) +
                " " +
                serialise(node.operator) +
                " " +
                serialiseSubExpression(node, node.right, associative = false)
        }

        override fun visit(node: PythonFunctionCallNode): String {
            return serialiseSubExpression(node, node.function, associative = true) +
                "(" +
                node.arguments.map(::serialise).joinToString(", ") +
                ")"
        }
    })
}

private fun serialiseSubExpression(
    parentNode: PythonExpressionNode,
    node: PythonExpressionNode,
    associative: Boolean
): String {
    val parentPrecedence = precedence(parentNode)
    val serialised = serialise(node)
    val subPrecedence = precedence(node)
    if (parentPrecedence > subPrecedence || parentPrecedence == subPrecedence && !associative) {
        return "(" + serialised + ")"
    } else {
        return serialised
    }
}

private fun serialise(operator: PythonOperator) = when(operator) {
    PythonOperator.EQUALS -> "=="
    PythonOperator.ADD -> "+"
    PythonOperator.SUBTRACT -> "-"
    PythonOperator.MULTIPLY -> "*"
}

private fun precedence(node: PythonExpressionNode): Int {
    return node.accept(object : PythonExpressionNode.Visitor<Int> {
        override fun visit(node: PythonBooleanLiteralNode): Int {
            return 18
        }

        override fun visit(node: PythonIntegerLiteralNode): Int {
            return 18
        }

        override fun visit(node: PythonVariableReferenceNode): Int {
            return 18
        }

        override fun visit(node: PythonBinaryOperationNode): Int {
            return when(node.operator) {
                PythonOperator.EQUALS -> 6
                PythonOperator.ADD -> 11
                PythonOperator.SUBTRACT -> 11
                PythonOperator.MULTIPLY -> 12
            }
        }

        override fun visit(node: PythonFunctionCallNode): Int {
            return 16
        }
    })
}
