package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

open class ReturnCheckError(message: String?, val source: Source) : Exception(message)

internal fun checkReturns(node: ModuleNode, types: Map<Int, Type>) {
    descendantsAndSelf(node).forEach({descendant -> when(descendant) {
        is FunctionNode -> checkReturns(descendant, types)
    }})
}

private fun checkReturns(node: FunctionNode, types: Map<Int, Type>) {
    // TODO: throw a CompilerError on failure
    val nodeType = types[node.nodeId] as? FunctionType
    when (nodeType) {
        null -> throw CompilerError(
            "type of ${node.name} is unknown",
            source = node.source
        )
    }
    if (nodeType != null && nodeType.returns != UnitType && !alwaysReturns(node.body)) {
        throw ReturnCheckError("function ${node.name} is missing return statement", node.source)
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
