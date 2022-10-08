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
    val definitionResolvers: MutableMap<Int, () -> Unit>
) {
    operator fun set(node: ReferenceNode, value: VariableBindingNode): Unit {
        nodes[node.nodeId] = value
    }

    fun initialise(node: VariableBindingNode) {
        isInitialised.add(node.nodeId)
    }

    fun define(node: VariableBindingNode, func: () -> Unit) {
        definitionResolvers[node.nodeId] = func
    }

    fun isInitialised(node: VariableBindingNode): Boolean {
        if (isInitialised.contains(node.nodeId)) {
            return true
        } else {
            val deferredInitialisation = definitionResolvers.remove(node.nodeId)
            if (deferredInitialisation == null) {
                return false
            } else {
                isInitialised.add(node.nodeId)
                deferredInitialisation()
                return true
            }
        }
    }

    fun enterScope(bindings: Map<Identifier, VariableBindingNode>): ResolutionContext {
        return ResolutionContext(this.bindings + bindings, nodes, isInitialised, definitionResolvers)
    }
}

internal fun resolveReferences(node: Node, globals: Map<Identifier, VariableBindingNode>): ResolvedReferences {
    val context = ResolutionContext(
        globals,
        mutableMapOf(),
        isInitialised = globals.values.map(VariableBindingNode::nodeId).toMutableSet(),
        definitionResolvers = mutableMapOf(),
    )

    val result = resolveEval(node)
    result.phaseDefine(context)
    result.phaseResolveImmediates(context)
    result.phaseResolveBodies(context)

    return ResolvedReferencesMap(context.nodes)
}

internal interface ResolutionResult {
    fun phaseDefine(context: ResolutionContext)
    fun phaseResolveImmediates(context: ResolutionContext)
    fun phaseResolveBodies(context: ResolutionContext)
}

private fun resolve(structure: NodeStructure): ResolutionResult {
    return when (structure) {
        is NodeStructure.Eval -> resolveEval(structure.node)
        // TODO: substructures should always be static
        is NodeStructure.TypeLevelEval -> resolveEval(structure.node)
        is NodeStructure.SubEnv -> resolveSubEnv(structure.structure)
        is NodeStructure.Define -> resolveDefine(structure)
        is NodeStructure.Initialise -> resolveInitialise(structure)
    }
}

internal fun resolveEval(node: Node): ResolutionResult {
    val subResults = node.structure.map { structure -> resolve(structure) }

    return object : ResolutionResult {
        override fun phaseDefine(context: ResolutionContext) {
            for (subResult in subResults) {
                subResult.phaseDefine(context)
            }
        }

        override fun phaseResolveImmediates(context: ResolutionContext) {
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

            for (subResult in subResults) {
                subResult.phaseResolveImmediates(context)
            }
        }

        override fun phaseResolveBodies(context: ResolutionContext) {
            for (subResult in subResults) {
                subResult.phaseResolveBodies(context)
            }
        }
    }
}

private fun resolveSubEnv(structures: List<NodeStructure>): ResolutionResult {
    return object : ResolutionResult {
        override fun phaseDefine(context: ResolutionContext) {
        }

        override fun phaseResolveImmediates(context: ResolutionContext) {
        }

        override fun phaseResolveBodies(context: ResolutionContext) {
            val binders = bindings(structures)
            val subEnvContext = enterScope(binders, context)

            val subEnvResults = structures.map { structure -> resolve(structure) }

            for (subEnvResult in subEnvResults) {
                subEnvResult.phaseDefine(subEnvContext)
            }
            for (subEnvResult in subEnvResults) {
                subEnvResult.phaseResolveImmediates(subEnvContext)
            }
            for (subEnvResult in subEnvResults) {
                subEnvResult.phaseResolveBodies(subEnvContext)
            }
        }
    }
}

private fun resolveDefine(structure: NodeStructure.Define): ResolutionResult {
    var isResolved = false

    return object : ResolutionResult {
        override fun phaseDefine(context: ResolutionContext) {
            context.define(structure.binding) { phaseResolveBodies(context) }
        }

        override fun phaseResolveImmediates(context: ResolutionContext) {

        }

        override fun phaseResolveBodies(context: ResolutionContext) {
            if (!isResolved) {
                val result = resolve(structure.structure)
                result.phaseDefine(context)
                result.phaseResolveImmediates(context)
                result.phaseResolveBodies(context)
                isResolved = true
            }
        }
    }
}

private fun resolveInitialise(structure: NodeStructure.Initialise): ResolutionResult {
    return object : ResolutionResult {
        override fun phaseDefine(context: ResolutionContext) {
        }

        override fun phaseResolveImmediates(context: ResolutionContext) {
            context.initialise(structure.binding)
        }

        override fun phaseResolveBodies(context: ResolutionContext) {
        }
    }
}

private fun bindings(structures: List<NodeStructure>): List<VariableBindingNode> {
    return structures.flatMap(::bindings)
}

private fun bindings(structure: NodeStructure): List<VariableBindingNode> {
    return when (structure) {
        is NodeStructure.Eval -> bindings(structure.node.structure)
        is NodeStructure.TypeLevelEval -> bindings(structure.node.structure)
        is NodeStructure.SubEnv -> listOf()
        is NodeStructure.Define -> listOf(structure.binding) + bindings(structure.structure)
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
