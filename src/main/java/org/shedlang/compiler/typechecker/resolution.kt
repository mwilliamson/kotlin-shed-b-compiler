package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.ArgumentNode
import org.shedlang.compiler.ast.FunctionNode
import org.shedlang.compiler.ast.Node
import org.shedlang.compiler.ast.ReferenceNode

class ResolutionContext(val bindings: Map<String, Int>, val nodes: MutableMap<Int, Int>) {
    // TODO: raise a more specific exception
    operator fun get(node: ReferenceNode): Int = nodes[node.nodeId]!!
    operator fun set(node: ReferenceNode, value: Int): Unit {
        nodes[node.nodeId] = value
    }

    fun enterScope(bindings: Map<String, Int>): ResolutionContext {
        return ResolutionContext(this.bindings + bindings, nodes)
    }
}

internal fun resolve(node: Node, context: ResolutionContext) {
    when (node) {
        is ReferenceNode -> {
            val referentId = context.bindings[node.name]
            when (referentId) {
                null ->  throw UnresolvedReferenceError(node.name, node.location)
                else -> context[node] = referentId
            }
        }

        is FunctionNode -> {
            resolve(node.returnType, context)
            node.arguments.forEach { argument -> resolve(argument, context) }
            val bindings = node.arguments.associateBy(ArgumentNode::name, Node::nodeId)
            val bodyContext = context.enterScope(bindings)
            for (statement in node.body) {
                resolve(statement, bodyContext)
            }
        }

        else -> {
            for (child in node.children) {
                resolve(child, context)
            }
        }
    }
}
