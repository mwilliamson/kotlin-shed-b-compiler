package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.FunctionType
import org.shedlang.compiler.types.UnitType

open class ReturnCheckError(message: String?, source: Source) : SourceError(message, source)

internal fun checkReturns(node: FunctionNode, type: FunctionType, nodeTypes: NodeTypes) {
    if (type.returns != UnitType && !alwaysReturns(node.body, nodeTypes = nodeTypes)) {
        throw ReturnCheckError(
            "function is missing return statement",
            source = node.source
        )
    }
}

internal fun alwaysReturns(node: StatementNode, nodeTypes: NodeTypes): Boolean {
    return node.accept(object : StatementNode.Visitor<Boolean> {
        override fun visit(node: ReturnNode): Boolean {
            return true
        }

        override fun visit(node: IfNode): Boolean {
            return node.conditionalBranches.all({ branch ->
                alwaysReturns(branch.body, nodeTypes = nodeTypes)
            }) && alwaysReturns(node.elseBranch, nodeTypes = nodeTypes)
        }

        override fun visit(node: ExpressionStatementNode): Boolean {
            return false
        }

        override fun visit(node: ValNode): Boolean {
            return false
        }
    })
}

private fun alwaysReturns(body: FunctionBody, nodeTypes: NodeTypes): Boolean {
    return when (body) {
        is FunctionBody.Expression -> true
        is FunctionBody.Statements -> alwaysReturns(body.nodes, nodeTypes = nodeTypes)
    }
}

private fun alwaysReturns(nodes: List<StatementNode>, nodeTypes: NodeTypes): Boolean {
    return nodes.any { node -> alwaysReturns(node, nodeTypes = nodeTypes) }
}
