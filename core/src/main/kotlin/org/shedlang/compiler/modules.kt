package org.shedlang.compiler

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.metaTypeToType

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
    fun typeOf(node: StaticNode): Type
    fun declaredType(node: TypeDeclarationNode): Type
}

val EMPTY_TYPES: Types = TypesMap(mapOf(), mapOf())

class TypesMap(
    private val expressionTypes: Map<Int, Type>,
    private val variableTypes: Map<Int, Type>
) : Types {
    override fun typeOf(node: ExpressionNode): Type {
        return expressionTypes[node.nodeId]!!
    }

    override fun typeOf(node: StaticNode): Type {
        return expressionTypes[node.nodeId]!!
    }

    override fun declaredType(node: TypeDeclarationNode): Type {
        return metaTypeToType(variableTypes[node.nodeId]!!)!!
    }
}
