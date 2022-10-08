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

    val result = resolutionStepsEval(node)
    for (phase in ResolutionPhase.values()) {
        result.runPhase(phase, context)
    }

    return ResolvedReferencesMap(context.nodes)
}

internal enum class ResolutionPhase {
    DEFINE,
    IMMEDIATES,
    RESOLVE_BODIES,
}

internal class ResolverStep(
    val phase: ResolutionPhase,
    val run: (context: ResolutionContext) -> Unit,
)

internal class ResolverSteps(private val steps: List<ResolverStep>) {
    fun runPhase(phase: ResolutionPhase, context: ResolutionContext) {
        for (step in steps) {
            if (step.phase == phase) {
                step.run(context)
            }
        }
    }
}

private fun resolutionSteps(structure: NodeStructure): ResolverSteps {
    return when (structure) {
        is NodeStructure.Eval -> resolutionStepsEval(structure.node)
        // TODO: substructures should always be static
        is NodeStructure.TypeLevelEval -> resolutionStepsEval(structure.node)
        is NodeStructure.Ref -> resolutionStepsRef(structure.node)
        is NodeStructure.SubEnv -> resolutionStepsSubEnv(structure.structure)
        is NodeStructure.Define -> resolutionStepsDefine(structure)
        is NodeStructure.Initialise -> resolutionStepsInitialise(structure)
    }
}

internal fun resolutionStepsEval(node: Node): ResolverSteps {
    val subStepSets = node.structure.map { structure -> resolutionSteps(structure) }

    return ResolverSteps(ResolutionPhase.values().map { phase ->
        ResolverStep(phase) { context ->
            for (subSteps in subStepSets) {
                subSteps.runPhase(phase, context)
            }
        }
    })
}

internal fun resolutionStepsRef(node: ReferenceNode): ResolverSteps {
    return ResolverSteps(listOf(
        ResolverStep(ResolutionPhase.IMMEDIATES) { context ->
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
    ))
}

private fun resolutionStepsSubEnv(structures: List<NodeStructure>): ResolverSteps {
    return ResolverSteps(listOf(
        ResolverStep(ResolutionPhase.RESOLVE_BODIES) { context ->
            val binders = bindings(structures)
            val subEnvContext = enterScope(binders, context)

            val subEnvStepSets = structures.map { structure -> resolutionSteps(structure) }

            for (phase in ResolutionPhase.values()) {
                for (subEnvSteps in subEnvStepSets) {
                    subEnvSteps.runPhase(phase, subEnvContext)
                }
            }
        }
    ))
}

private fun resolutionStepsDefine(structure: NodeStructure.Define): ResolverSteps {
    var isResolved = false

    fun resolveBodies(context: ResolutionContext) {
        if (!isResolved) {
            val result = resolutionSteps(structure.structure)
            for (phase in ResolutionPhase.values()) {
                result.runPhase(phase, context)
            }
            isResolved = true
        }
    }

    return ResolverSteps(listOf(
        ResolverStep(ResolutionPhase.DEFINE) { context ->
            context.define(structure.binding) { resolveBodies(context) }
        },
        ResolverStep(ResolutionPhase.RESOLVE_BODIES) { context ->
            resolveBodies(context)
        },
    ))
}

private fun resolutionStepsInitialise(structure: NodeStructure.Initialise): ResolverSteps {
    return ResolverSteps(listOf(
        ResolverStep(ResolutionPhase.IMMEDIATES) { context ->
            context.initialise(structure.binding)
        }
    ))
}

private fun bindings(structures: List<NodeStructure>): List<VariableBindingNode> {
    return structures.flatMap(::bindings)
}

private fun bindings(structure: NodeStructure): List<VariableBindingNode> {
    return when (structure) {
        is NodeStructure.Eval -> bindings(structure.node.structure)
        is NodeStructure.TypeLevelEval -> bindings(structure.node.structure)
        is NodeStructure.Ref -> listOf()
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
