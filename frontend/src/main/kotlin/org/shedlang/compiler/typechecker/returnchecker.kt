package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

open class ReturnCheckError(message: String?, val source: Source) : Exception(message)

internal fun checkReturns(node: ModuleNode) {
    node.body.forEach(::checkReturns)
}

private fun checkReturns(node: FunctionNode) {
    if (!alwaysReturns(node.body)) {
        throw ReturnCheckError("function ${node.name} is missing return statement", node.source)
    }
}

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
