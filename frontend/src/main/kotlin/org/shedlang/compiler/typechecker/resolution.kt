package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.nullableToList

class ResolvedReferencesMap(private val references: Map<Int, VariableBindingNode>) : ResolvedReferences {
    override fun get(node: ReferenceNode): VariableBindingNode {
        val targetNode = references[node.nodeId]
        if (targetNode == null) {
            throw CompilerError(
                "reference ${node.name} is unresolved",
                source = node.source
            )
        } else {
            return targetNode
        }

    }
}

internal class ResolutionContext(
    val bindings: Map<String, VariableBindingNode>,
    val nodes: MutableMap<Int, VariableBindingNode>,
    val isInitialised: MutableSet<Int>,
    val deferred: MutableMap<Int, () -> Unit>
) {
    operator fun set(node: ReferenceNode, value: VariableBindingNode): Unit {
        nodes[node.nodeId] = value
    }

    fun initialise(node: VariableBindingNode) {
        isInitialised.add(node.nodeId)
    }

    fun defer(node: VariableBindingNode, func: () -> Unit) {
        deferred[node.nodeId] = func
    }

    fun isInitialised(node: VariableBindingNode): Boolean {
        if (isInitialised.contains(node.nodeId)) {
            return true
        } else {
            val deferredInitialisation = deferred[node.nodeId]
            if (deferredInitialisation == null) {
                return false
            } else {
                undefer(node.nodeId, deferredInitialisation)
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

    fun enterScope(bindings: Map<String, VariableBindingNode>): ResolutionContext {
        return ResolutionContext(this.bindings + bindings, nodes, isInitialised, deferred)
    }
}

internal fun resolve(node: Node, globals: Map<String, VariableBindingNode>): ResolvedReferences {
    val context = ResolutionContext(
        globals,
        mutableMapOf(),
        isInitialised = globals.values.map(VariableBindingNode::nodeId).toMutableSet(),
        deferred = mutableMapOf()
    )
    resolve(node, context)
    context.undefer()
    return ResolvedReferencesMap(context.nodes)
}

internal fun resolve(node: Node, context: ResolutionContext) {
    when (node) {
        is ReferenceNode -> {
            val referent = context.bindings[node.name]
            if (referent == null) {
                throw UnresolvedReferenceError(node.name, node.source)
            } else {
                context[node] = referent
                if (!context.isInitialised(referent)) {
                    throw UninitialisedVariableError(node.name, node.source)
                }
            }
        }

        is FunctionDeclarationNode -> {
            context.defer(node, {
                resolveFunction(node, context)
            })
        }

        is FunctionExpressionNode -> resolveFunction(node, context)

        is FunctionTypeNode -> resolveFunctionType(node, context)

        is ShapeNode -> {
            context.defer(node, {
                resolveScope(
                    binders = node.staticParameters,
                    body = node.hasTagValueFor.nullableToList() + node.fields,
                    context = context
                )
            })
        }

        is UnionNode -> {
            context.defer(node, {
                resolveScope(
                    binders = node.staticParameters,
                    body = node.superType.nullableToList() + node.members,
                    context = context
                )
            })
        }

        is ValNode -> {
            resolve(node.expression, context)
            context.initialise(node)
        }

        is ModuleNode -> {
            resolveScope(body = node.imports + node.body, context = context)
        }

        is TypesModuleNode -> {
            resolveScope(body = node.imports + node.body, context = context)
        }

        is ImportNode -> {
            context.initialise(node)
        }

        is IfNode -> {
            for (conditionalBranch in node.conditionalBranches) {
                resolve(conditionalBranch.condition, context)
                resolveScope(body = conditionalBranch.body, context = context)
            }
            resolveScope(body = node.elseBranch, context = context)
        }

        is WhenNode -> {
            resolve(node.expression, context)
            for (branch in node.branches) {
                resolve(branch.type, context)
                resolveScope(branch.body, context = context)
            }
        }

        else -> {
            for (child in node.children) {
                resolve(child, context)
            }
        }
    }
}

private fun resolveFunction(node: FunctionNode, context: ResolutionContext) {
    // TODO: test namedParameters resolution
    val bodyContext = resolveScope(
        binders = node.staticParameters,
        body = node.effects + listOf(node.returnType).filterNotNull() + node.parameters + node.namedParameters,
        context = context
    )
    resolveScope(
        body = node.body.nodes,
        binders = node.parameters + node.namedParameters,
        context = bodyContext
    )
}

private fun resolveFunctionType(node: FunctionTypeNode, context: ResolutionContext) {
    resolveScope(
        binders = node.staticParameters,
        body = node.positionalParameters + node.namedParameters + node.effects + listOf(node.returnType),
        context = context
    )
}

private fun resolveScope(
    body: List<Node> = listOf(),
    binders: List<VariableBindingNode> = listOf(),
    context: ResolutionContext
): ResolutionContext {
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

    return bodyContext
}

private fun enterScope(binders: List<VariableBindingNode>, context: ResolutionContext): ResolutionContext {
    val bindings = binders.groupBy(VariableBindingNode::name)
        .mapValues(fun(entry): VariableBindingNode {
            if (entry.value.size > 1) {
                throw RedeclarationError(entry.key, entry.value[1].source)
            } else {
                return entry.value[0]
            }
        })

    return context.enterScope(bindings)
}
