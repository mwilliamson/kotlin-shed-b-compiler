package org.shedlang.compiler.typechecker

import org.shedlang.compiler.types.FunctionType
import org.shedlang.compiler.types.UnitType
import org.shedlang.compiler.ast.*

open class ReturnCheckError(message: String?, val source: Source) : Exception(message)

internal fun checkReturns(node: ModuleNode, types: NodeTypes) {
    descendantsAndSelf(node).forEach({descendant -> when(descendant) {
        is FunctionNode -> checkReturns(descendant, types)
    }})
}

private fun checkReturns(node: FunctionNode, types: NodeTypes) {
    val nodeType = types.typeOf(node)
    if (nodeType is FunctionType) {
        if (nodeType.returns != UnitType && !alwaysReturns(node.body)) {
            throw ReturnCheckError(
                "function ${node.name} is missing return statement",
                source = node.source
            )
        }
    } else {
        throw NotFunctionTypeError(nodeType, source = node.source)
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

private fun alwaysReturns(nodes: List<StatementNode>): Boolean {
    return nodes.any(::alwaysReturns)
}
