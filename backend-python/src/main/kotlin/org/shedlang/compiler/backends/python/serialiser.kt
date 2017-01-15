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
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonFunctionCallNode): String {
            throw UnsupportedOperationException("not implemented")
        }

    })
}
