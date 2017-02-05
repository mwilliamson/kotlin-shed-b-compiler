package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import java.util.*

interface VariableReferences {
    operator fun get(node: ReferenceNode): Int?
}

internal class ResolutionContext(
    val bindings: Map<String, Int>,
    val nodes: MutableMap<Int, Int>,
    val isInitialised: MutableSet<Int>,
    val deferred: MutableMap<Int, () -> Unit>
): VariableReferences {
    override operator fun get(node: ReferenceNode): Int? = nodes[node.nodeId]
    operator fun set(node: ReferenceNode, value: Int): Unit {
        nodes[node.nodeId] = value
    }

    fun initialise(node: VariableBindingNode) {
        isInitialised.add(node.nodeId)
    }

    fun defer(node: VariableBindingNode, func: () -> Unit) {
        deferred[node.nodeId] = func
    }

    fun isInitialised(nodeId: Int): Boolean {
        if (isInitialised.contains(nodeId)) {
            return true
        } else {
            val deferredInitialisation = deferred[nodeId]
            if (deferredInitialisation == null) {
                return false
            } else {
                undefer(nodeId, deferredInitialisation)
                return true
            }
        }
    }

    fun undefer() {
        for (deferredEntry in deferred) {
            if (!isInitialised.contains(deferredEntry.key)) {
                undefer(deferredEntry.key, deferredEntry.value)
            }
        }
    }

    private fun undefer(nodeId: Int, deferredInitialisation: () -> Unit) {
        isInitialised.add(nodeId)
        deferredInitialisation()
    }

    fun enterScope(bindings: Map<String, Int>): ResolutionContext {
        return ResolutionContext(this.bindings + bindings, nodes, isInitialised, deferred)
    }
}

internal fun resolve(node: Node, globals: Map<String, Int>): VariableReferences {
    val context = ResolutionContext(
        globals,
        mutableMapOf(),
        isInitialised = HashSet(globals.values),
        deferred = mutableMapOf()
    )
    resolve(node, context)
    context.undefer()
    return context
}

internal fun resolve(node: Node, context: ResolutionContext) {
    when (node) {
        is ReferenceNode -> {
            val referentId = context.bindings[node.name]
            if (referentId == null) {
                throw UnresolvedReferenceError(node.name, node.source)
            } else {
                context[node] = referentId
                if (!context.isInitialised(referentId)) {
                    throw UninitialisedVariableError(node.name, node.source)
                }
            }
        }

        is FunctionNode -> {
            resolve(node.returnType, context)
            node.arguments.forEach { argument -> resolve(argument, context) }
            context.defer(node, {
                resolveScope(
                    body = node.body,
                    binders = node.arguments,
                    context = context
                )
            })
        }

        is ValNode -> {
            context.initialise(node)
            resolve(node.expression, context)
        }

        is ModuleNode -> {
            resolveScope(body = node.body, context = context)
        }

        is IfStatementNode -> {
            resolve(node.condition, context)
            resolveScope(body = node.trueBranch, context = context)
            resolveScope(body = node.falseBranch, context = context)
        }

        else -> {
            for (child in node.children) {
                resolve(child, context)
            }
        }
    }
}

private fun <T: Node> resolveScope(
    body: List<T>,
    binders: List<VariableBindingNode> = listOf(),
    context: ResolutionContext
) {
    val bodyContext = enterScope(
        binders + body.filterIsInstance<VariableBindingNode>(),
        context
    )

    for (binder in binders) {
        bodyContext.initialise(binder)
    }

    for (child in body) {
        resolve(child, bodyContext)
    }
}

private fun enterScope(binders: List<VariableBindingNode>, context: ResolutionContext): ResolutionContext {
    val bindings = binders.groupBy(VariableBindingNode::name)
        .mapValues(fun(entry): Int {
            if (entry.value.size > 1) {
                throw RedeclarationError(entry.key, entry.value[1].source)
            } else {
                return entry.value[0].nodeId
            }
        })

    return context.enterScope(bindings)
}
