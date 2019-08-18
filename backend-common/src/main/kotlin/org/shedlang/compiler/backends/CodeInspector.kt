package org.shedlang.compiler.backends

import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.findDiscriminator
import org.shedlang.compiler.findDiscriminatorForCast
import org.shedlang.compiler.types.Discriminator
import org.shedlang.compiler.types.Type

interface CodeInspector {
    fun discriminatorForCast(node: CallBaseNode): Discriminator
    fun discriminatorForIsExpression(node: IsNode): Discriminator
    fun discriminatorForWhenBranch(node: WhenNode, branch: WhenBranchNode): Discriminator
    fun isCast(node: CallBaseNode): Boolean
    fun resolve(node: ReferenceNode): VariableBindingNode
    fun typeOfExpression(node: ExpressionNode): Type
}

class ModuleCodeInspector(private val module: Module.Shed): CodeInspector {
    override fun discriminatorForCast(node: CallBaseNode): Discriminator {
        return findDiscriminatorForCast(node, types = module.types)
    }

    override fun discriminatorForIsExpression(node: IsNode): Discriminator {
        return findDiscriminator(node, types = module.types)
    }

    override fun discriminatorForWhenBranch(node: WhenNode, branch: WhenBranchNode): Discriminator {
        return findDiscriminator(node, branch, types = module.types)
    }

    override fun isCast(node: CallBaseNode): Boolean {
        return org.shedlang.compiler.isCast(node, references = module.references)
    }

    override fun resolve(node: ReferenceNode): VariableBindingNode {
        return module.references[node]
    }

    override fun typeOfExpression(node: ExpressionNode): Type {
        return module.types.typeOf(node)
    }
}

class FakeCodeInspector(
    private val discriminatorsForCasts: Map<CallBaseNode, Discriminator> = mapOf(),
    private val discriminatorsForIsExpressions: Map<IsNode, Discriminator> = mapOf(),
    private val discriminatorsForWhenBranches: Map<Pair<WhenNode, WhenBranchNode>, Discriminator> = mapOf(),
    private val expressionTypes: Map<ExpressionNode, Type> = mapOf(),
    private val references: Map<ReferenceNode, VariableBindingNode> = mapOf()
): CodeInspector {
    override fun discriminatorForCast(node: CallBaseNode): Discriminator {
        return discriminatorsForCasts[node] ?: error("missing discriminator for node: $node")
    }

    override fun discriminatorForIsExpression(node: IsNode): Discriminator {
        return discriminatorsForIsExpressions[node] ?: error("missing discriminator for node: $node")
    }

    override fun discriminatorForWhenBranch(node: WhenNode, branch: WhenBranchNode): Discriminator {
        return discriminatorsForWhenBranches[Pair(node, branch)] ?: error("missing discriminator for when: $node, $branch")
    }

    override fun isCast(node: CallBaseNode): Boolean {
        return false
    }

    override fun resolve(node: ReferenceNode): VariableBindingNode {
        return references[node] ?: error("unresolved node: $node")
    }

    override fun typeOfExpression(node: ExpressionNode): Type {
        return expressionTypes[node] ?: error("expression without type: $node")
    }
}
