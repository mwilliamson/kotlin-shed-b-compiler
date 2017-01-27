package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

internal fun alwaysReturns(node: StatementNode): Boolean {
    return node.accept(object : StatementNodeVisitor<Boolean> {
        override fun visit(node: ReturnNode): Boolean {
            return true
        }

        override fun visit(node: IfStatementNode): Boolean {
            return alwaysReturns(node.trueBranch) && alwaysReturns(node.falseBranch)
        }

        override fun visit(node: ExpressionStatementNode): Boolean {
            return false
        }
    })
}

private fun alwaysReturns(nodes: List<StatementNode>): Boolean {
    return nodes.any(::alwaysReturns)
}
