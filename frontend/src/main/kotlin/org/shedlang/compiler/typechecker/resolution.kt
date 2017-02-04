package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

// TODO: throw error if name is declared more than once in same scope

interface VariableReferences {
    operator fun get(node: ReferenceNode): Int?
}

internal class ResolutionContext(val bindings: Map<String, Int>, val nodes: MutableMap<Int, Int>): VariableReferences {
    override operator fun get(node: ReferenceNode): Int? = nodes[node.nodeId]
    operator fun set(node: ReferenceNode, value: Int): Unit {
        nodes[node.nodeId] = value
    }

    fun enterScope(bindings: Map<String, Int>): ResolutionContext {
        return ResolutionContext(this.bindings + bindings, nodes)
    }
}

internal fun resolve(node: Node, globals: Map<String, Int>): VariableReferences {
    val context = ResolutionContext(globals, mutableMapOf())
    resolve(node, context)
    return context
}

internal fun resolve(node: Node, context: ResolutionContext) {
    when (node) {
        is ReferenceNode -> {
            val referentId = context.bindings[node.name]
            when (referentId) {
                null ->  throw UnresolvedReferenceError(node.name, node.source)
                else -> context[node] = referentId
            }
        }

        is FunctionNode -> {
            resolve(node.returnType, context)
            node.arguments.forEach { argument -> resolve(argument, context) }
            val binders = node.arguments + node.body.filterIsInstance<VariableBindingNode>()
            val bindings = binders.associateBy(VariableBindingNode::name, Node::nodeId)
            val bodyContext = context.enterScope(bindings)
            for (statement in node.body) {
                resolve(statement, bodyContext)
            }
        }

        is ModuleNode -> {
            val bindings = node.body.associateBy(FunctionNode::name, Node::nodeId)
            val bodyContext = context.enterScope(bindings)
            for (function in node.body) {
                resolve(function, bodyContext)
            }
        }

        else -> {
            for (child in node.children) {
                resolve(child, context)
            }
        }
    }
}
