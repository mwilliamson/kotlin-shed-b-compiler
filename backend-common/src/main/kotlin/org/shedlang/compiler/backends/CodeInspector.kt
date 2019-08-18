package org.shedlang.compiler.backends

import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.findDiscriminator
import org.shedlang.compiler.findDiscriminatorForCast
import org.shedlang.compiler.types.Discriminator
import org.shedlang.compiler.types.SymbolType
import org.shedlang.compiler.types.Type

interface CodeInspector {
    fun discriminatorForCast(node: CallBaseNode): Discriminator
    fun discriminatorForIsExpression(node: IsNode): Discriminator
    fun discriminatorForWhenBranch(node: WhenNode, branch: WhenBranchNode): Discriminator
    fun isCast(node: CallBaseNode): Boolean
    fun resolve(node: ReferenceNode): VariableBindingNode
    fun shapeFields(node: ShapeBaseNode): List<FieldInspector>
    fun typeOfExpression(node: ExpressionNode): Type
}

data class FieldInspector(
    val name: Identifier,
    val value: FieldValue?,
    val source: Source
)

val FieldInspector.isConstant: Boolean
    get() = value != null

sealed class FieldValue {
    data class Expression(val expression: ExpressionNode): FieldValue()
    data class Symbol(val symbol: org.shedlang.compiler.types.Symbol): FieldValue()
}

class ModuleCodeInspector(private val module: Module.Shed): CodeInspector {
    override fun discriminatorForCast(node: CallBaseNode): Discriminator {
        return findDiscriminatorForCast(node, types = module.types)
    }

    override fun discriminatorForIsExpression(node: IsNode): Discriminator {
        return module.types.discriminatorForIsExpression(node)
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

    override fun shapeFields(node: ShapeBaseNode): List<FieldInspector> {
        return module.types.shapeFields(node).values.map { field ->
            val fieldNode = node.fields
                .find { fieldNode -> fieldNode.name == field.name }
            val fieldSource = NodeSource(fieldNode ?: node)

            val fieldValueNode = fieldNode?.value
            val fieldType = field.type
            val value = if (fieldValueNode != null) {
                FieldValue.Expression(fieldValueNode)
            } else if (fieldType is SymbolType) {
                FieldValue.Symbol(fieldType.symbol)
            } else if (field.isConstant) {
                // TODO: throw better exception
                throw Exception("Could not find value for constant field")
            } else {
                null
            }
            FieldInspector(
                name = field.name,
                value = value,
                source = fieldSource
            )
        }
    }

    override fun typeOfExpression(node: ExpressionNode): Type {
        return module.types.typeOf(node)
    }
}

class SimpleCodeInspector(
    private val discriminatorsForCasts: Map<CallBaseNode, Discriminator> = mapOf(),
    private val discriminatorsForIsExpressions: Map<IsNode, Discriminator> = mapOf(),
    private val discriminatorsForWhenBranches: Map<Pair<WhenNode, WhenBranchNode>, Discriminator> = mapOf(),
    private val expressionTypes: Map<ExpressionNode, Type> = mapOf(),
    private val references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
    private val shapeFields: Map<ShapeBaseNode, List<FieldInspector>> = mapOf()
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

    override fun shapeFields(node: ShapeBaseNode): List<FieldInspector> {
        return shapeFields[node] ?: error("missing fields for node: $node")
    }

    override fun typeOfExpression(node: ExpressionNode): Type {
        return expressionTypes[node] ?: error("expression without type: $node")
    }
}
