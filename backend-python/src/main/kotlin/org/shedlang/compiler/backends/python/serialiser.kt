package org.shedlang.compiler.backends.python

import org.shedlang.compiler.backends.python.ast.*

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
            val precedence = precedence(node)
            return serialise(node.left, precedence, associative = true) +
                " " +
                serialise(node.operator) +
                " " +
                serialise(node.right, precedence, associative = false)
        }

        override fun visit(node: PythonFunctionCallNode): String {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

fun serialise(node: PythonExpressionNode, precedence: Int, associative: Boolean): String {
    val serialised = serialise(node)
    val subPrecedence = precedence(node)
    if (precedence > subPrecedence || precedence == subPrecedence && !associative) {
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
