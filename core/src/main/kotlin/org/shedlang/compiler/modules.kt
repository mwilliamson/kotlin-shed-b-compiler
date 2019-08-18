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

    fun discriminatorForIsExpression(node: IsNode): Discriminator
    fun discriminatorForWhenBranch(node: WhenBranchNode): Discriminator
}

fun findDiscriminatorForCast(node: CallBaseNode, types: Types): Discriminator {
    return findDiscriminator(
        sourceType = metaTypeToType(types.typeOf(node.positionalArguments[0]))!!,
        targetType = metaTypeToType(types.typeOf(node.positionalArguments[1]))!!
    )!!
}

val EMPTY_TYPES: Types = TypesMap(mapOf(), mapOf(), mapOf())

class TypesMap(
    private val discriminators: Map<Int, Discriminator>,
    private val expressionTypes: Map<Int, Type>,
    private val variableTypes: Map<Int, Type>
) : Types {
    override fun discriminatorForIsExpression(node: IsNode): Discriminator {
        return discriminators[node.nodeId]!!
    }

    override fun discriminatorForWhenBranch(node: WhenBranchNode): Discriminator {
        return discriminators[node.nodeId]!!
    }

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
