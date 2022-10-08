package org.shedlang.compiler.typechecker

import org.shedlang.compiler.InternalCompilerError
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*

class ResolvedReferencesMap(private val references: Map<Int, VariableBindingNode>) : ResolvedReferences {
    override fun get(node: ReferenceNode): VariableBindingNode {
        val targetNode = references[node.nodeId]
        if (targetNode == null) {
            throw InternalCompilerError(
                "reference ${node.name.value} is unresolved",
                source = node.source
            )
        } else {
            return targetNode
        }
    }

    override val referencedNodes: Collection<VariableBindingNode>
        get() = references.values

    companion object {
        val EMPTY = ResolvedReferencesMap(mapOf())
    }
}

internal class ResolutionContext(
    val bindings: Map<Identifier, VariableBindingNode>,
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
        while (deferred.isNotEmpty()) {
            val nodeId = deferred.keys.first()
            val deferredInitialisation = deferred.remove(nodeId)
            if (deferredInitialisation != null) {
                // TODO: is this check necessary?
                if (!isInitialised.contains(nodeId)) {
                    undefer(nodeId, deferredInitialisation)
                }
            }
        }
    }

    private fun undefer(nodeId: Int, deferredInitialisation: () -> Unit) {
        isInitialised.add(nodeId)
        deferredInitialisation()
    }

    fun enterScope(bindings: Map<Identifier, VariableBindingNode>): ResolutionContext {
        return ResolutionContext(this.bindings + bindings, nodes, isInitialised, deferred)
    }
}

fun resolve(node: Node, globals: Map<Identifier, VariableBindingNode>): ResolvedReferences {
    val context = ResolutionContext(
        globals,
        mutableMapOf(),
        isInitialised = globals.values.map(VariableBindingNode::nodeId).toMutableSet(),
        deferred = mutableMapOf()
    )
    resolveEval(node, context)
    context.undefer()
    return ResolvedReferencesMap(context.nodes)
}

internal fun resolve(structure: NodeStructure, context: ResolutionContext) {
    when (structure) {
        is NodeStructure.Eval -> resolveEval(structure.node, context)
        // TODO: substructures should always be static
        is NodeStructure.TypeLevelEval -> resolveEval(structure.node, context)
        is NodeStructure.SubEnv -> resolveSubEnv(structure.structure, context)
        is NodeStructure.DeferInitialise -> context.defer(structure.binding) { resolve(structure.structure, context) }
        is NodeStructure.Initialise -> context.initialise(structure.binding)
    }
}

internal fun resolveEval(node: Node, context: ResolutionContext) {
    if (node is ReferenceNode) {
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

    node.structure.forEach { structure -> resolve(structure, context) }
}

internal fun resolveSubEnv(structures: List<NodeStructure>, context: ResolutionContext) {
    val binders = bindings(structures)
    val subEnvContext = enterScope(binders, context)

    structures.forEach { structure -> resolve(structure, subEnvContext) }
}

private fun bindings(structures: List<NodeStructure>): List<VariableBindingNode> {
    return structures.flatMap(::bindings)
}

private fun bindings(structure: NodeStructure): List<VariableBindingNode> {
    return when (structure) {
        is NodeStructure.Eval -> bindings(structure.node.structure)
        is NodeStructure.TypeLevelEval -> bindings(structure.node.structure)
        is NodeStructure.SubEnv -> listOf()
        is NodeStructure.DeferInitialise -> listOf(structure.binding) + bindings(structure.structure)
        is NodeStructure.Initialise -> listOf(structure.binding)
    }
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
