package org.shedlang.compiler.backends

import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

interface CodeInspector {
    fun discriminatorForCast(node: CallBaseNode): Discriminator
    fun discriminatorForIsExpression(node: IsNode): Discriminator
    fun discriminatorForWhenBranch(node: WhenNode, branch: WhenBranchNode): Discriminator
    fun resolve(node: ReferenceNode): VariableBindingNode
    fun shapeFields(node: ShapeBaseNode): List<FieldInspector>
    fun shapeTagValue(node: ShapeBaseNode): TagValue?
    fun typeOfExpression(node: ExpressionNode): Type
    fun functionType(node: FunctionNode): FunctionType
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
}

class ModuleCodeInspector(private val module: Module.Shed): CodeInspector {
    override fun discriminatorForCast(node: CallBaseNode): Discriminator {
        return module.types.discriminatorForCast(node)
    }

    override fun discriminatorForIsExpression(node: IsNode): Discriminator {
        return module.types.discriminatorForIsExpression(node)
    }

    override fun discriminatorForWhenBranch(node: WhenNode, branch: WhenBranchNode): Discriminator {
        return module.types.discriminatorForWhenBranch(branch)
    }

    override fun resolve(node: ReferenceNode): VariableBindingNode {
        return module.references[node]
    }

    override fun shapeFields(node: ShapeBaseNode): List<FieldInspector> {
        val shapeType = shapeType(node)
        return shapeType.allFields.values.map { field ->
            val fieldNode = node.fields
                .find { fieldNode -> fieldNode.name == field.name }
            val fieldSource = NodeSource(fieldNode ?: node)

            val fieldValueNode = fieldNode?.value
            val value = if (fieldValueNode != null) {
                FieldValue.Expression(fieldValueNode)
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

    override fun shapeTagValue(node: ShapeBaseNode): TagValue? {
        return shapeType(node).tagValue
    }

    private fun shapeType(node: ShapeBaseNode) =
        rawValue(module.types.declaredType(node)) as ShapeType

    override fun typeOfExpression(node: ExpressionNode): Type {
        return module.types.typeOfExpression(node)
    }

    override fun functionType(node: FunctionNode): FunctionType {
        return module.types.functionType(node)
    }
}

class SimpleCodeInspector(
    private val discriminatorsForCasts: Map<CallBaseNode, Discriminator> = mapOf(),
    private val discriminatorsForIsExpressions: Map<IsNode, Discriminator> = mapOf(),
    private val discriminatorsForWhenBranches: Map<Pair<WhenNode, WhenBranchNode>, Discriminator> = mapOf(),
    private val expressionTypes: Map<ExpressionNode, Type> = mapOf(),
    private val references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
    private val shapeFields: Map<ShapeBaseNode, List<FieldInspector>> = mapOf(),
    private val shapeTagValues: Map<ShapeBaseNode, TagValue?> = mapOf(),
    private val functionTypes: Map<FunctionNode, FunctionType> = mapOf()
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

    override fun resolve(node: ReferenceNode): VariableBindingNode {
        return references[node] ?: error("unresolved node: $node")
    }

    override fun shapeFields(node: ShapeBaseNode): List<FieldInspector> {
        return shapeFields[node] ?: error("missing fields for node: $node")
    }

    override fun shapeTagValue(node: ShapeBaseNode): TagValue? {
        return shapeTagValues[node]
    }

    override fun typeOfExpression(node: ExpressionNode): Type {
        return expressionTypes[node] ?: error("expression without type: $node")
    }

    override fun functionType(node: FunctionNode): FunctionType {
        return functionTypes[node] ?: error("function without type: $node")
    }
}
