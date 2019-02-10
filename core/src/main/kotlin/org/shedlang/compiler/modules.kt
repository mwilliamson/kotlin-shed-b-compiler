package org.shedlang.compiler

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

class ModuleSet(val modules: Collection<Module>)

sealed class Module {
    abstract val name: List<Identifier>
    abstract val type: ModuleType

    class Shed(
        override val name: List<Identifier>,
        val node: ModuleNode,
        override val type: ModuleType,
        val types: Types,
        val references: ResolvedReferences
    ): Module() {
        fun hasMain() = node.body.any({ node ->
            node is FunctionDeclarationNode && node.name.value == "main"
        })
    }

    class Native(
        override val name: List<Identifier>,
        override val type: ModuleType
    ): Module()
}


interface Types {
    fun typeOf(node: ExpressionNode): Type
    fun typeOf(node: StaticExpressionNode): Type
    fun declaredType(node: TypeDeclarationNode): Type
    fun shapeFields(node: ShapeBaseNode): Map<Identifier, Field> {
        return (rawType(declaredType(node)) as ShapeType).fields
    }

    fun findDiscriminator(expression: ExpressionNode, type: StaticExpressionNode): Discriminator {
        val sourceType = typeOf(expression)
        val targetType = metaTypeToType(typeOf(type))!!
        return findDiscriminator(sourceType = sourceType, targetType = targetType)!!
    }
}

fun findDiscriminator(node: IsNode, types: Types): Discriminator {
    return types.findDiscriminator(node.expression, node.type)
}

fun findDiscriminator(node: WhenNode, branch: WhenBranchNode, types: Types): Discriminator {
    return types.findDiscriminator(node.expression, branch.type)
}

val EMPTY_TYPES: Types = TypesMap(mapOf(), mapOf())

class TypesMap(
    private val expressionTypes: Map<Int, Type>,
    private val variableTypes: Map<Int, Type>
) : Types {
    override fun typeOf(node: ExpressionNode): Type {
        return expressionTypes[node.nodeId]!!
    }

    override fun typeOf(node: StaticExpressionNode): Type {
        return expressionTypes[node.nodeId]!!
    }

    override fun declaredType(node: TypeDeclarationNode): Type {
        return metaTypeToType(variableTypes[node.nodeId]!!)!!
    }
}
