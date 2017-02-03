package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.serialiseCStringLiteral

internal fun serialise(node: ExpressionNode) : String {
    return node.accept(object : ExpressionNodeVisitor<String> {
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
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionCallNode): String {
            throw UnsupportedOperationException("not implemented")
        }

    })
}
