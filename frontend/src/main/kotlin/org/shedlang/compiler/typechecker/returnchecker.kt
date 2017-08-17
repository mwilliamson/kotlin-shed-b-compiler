package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.FunctionType
import org.shedlang.compiler.types.UnitType

open class ReturnCheckError(message: String?, val source: Source) : Exception(message)

internal fun checkReturns(node: FunctionNode, type: FunctionType) {
    if (type.returns != UnitType && !alwaysReturns(node.body)) {
        throw ReturnCheckError(
            "function is missing return statement",
            source = node.source
        )
    }
}

internal fun alwaysReturns(node: StatementNode): Boolean {
    return node.accept(object : StatementNode.Visitor<Boolean> {
        override fun visit(node: ReturnNode): Boolean {
            return true
        }

        override fun visit(node: IfStatementNode): Boolean {
            return alwaysReturns(node.trueBranch) && alwaysReturns(node.falseBranch)
        }

        override fun visit(node: ExpressionStatementNode): Boolean {
            return false
        }

        override fun visit(node: ValNode): Boolean {
            return false
        }
    })
}

private fun alwaysReturns(body: FunctionBody): Boolean {
    return when (body) {
        is FunctionBody.Expression -> true
        is FunctionBody.Statements -> alwaysReturns(body.nodes)
    }
}

private fun alwaysReturns(nodes: List<StatementNode>): Boolean {
    return nodes.any(::alwaysReturns)
}
