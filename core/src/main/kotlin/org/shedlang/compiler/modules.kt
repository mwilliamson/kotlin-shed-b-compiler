package org.shedlang.compiler

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

class ModuleSet(val modules: Collection<Module>) {
    fun module(name: ModuleName): Module? {
        return modules.find { module -> module.name == name }
    }

    fun moduleType(name: ModuleName): ModuleType? {
        return module(name)?.type
    }
}

sealed class Module {
    abstract val name: ModuleName
    abstract val type: ModuleType

    class Shed(
        override val name: ModuleName,
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
        override val name: ModuleName,
        override val type: ModuleType
    ): Module()
}


interface Types {
    fun typeOfExpression(node: ExpressionNode): Type
    fun typeOfStaticExpression(node: StaticExpressionNode): Type
    fun typeOfTarget(target: TargetNode): Type
    fun declaredType(node: TypeDeclarationNode): StaticValue
    fun functionType(node: FunctionNode): FunctionType
    fun variableType(node: VariableBindingNode): Type

    fun discriminatorForCast(node: CallBaseNode): Discriminator
    fun discriminatorForIsExpression(node: IsNode): Discriminator
    fun discriminatorForWhenBranch(node: WhenBranchNode): Discriminator
}

val EMPTY_TYPES: Types = TypesMap(mapOf(), mapOf(), mapOf(), mapOf(), mapOf())

class TypesMap(
    private val discriminators: Map<Int, Discriminator>,
    private val expressionTypes: Map<Int, Type>,
    private val functionTypes: Map<Int, FunctionType>,
    private val targetTypes: Map<Int, Type>,
    private val variableTypes: Map<Int, Type>
) : Types {
    override fun discriminatorForCast(node: CallBaseNode): Discriminator {
        return discriminators[node.nodeId]!!
    }

    override fun discriminatorForIsExpression(node: IsNode): Discriminator {
        return discriminators[node.nodeId]!!
    }

    override fun discriminatorForWhenBranch(node: WhenBranchNode): Discriminator {
        return discriminators[node.nodeId]!!
    }

    override fun typeOfExpression(node: ExpressionNode): Type {
        return expressionTypes[node.nodeId]!!
    }

    override fun typeOfTarget(target: TargetNode): Type {
        return targetTypes[target.nodeId]!!
    }

    override fun typeOfStaticExpression(node: StaticExpressionNode): Type {
        return expressionTypes[node.nodeId]!!
    }

    override fun declaredType(node: TypeDeclarationNode): StaticValue {
        val type = variableTypes[node.nodeId]
        if (type is StaticValueType) {
            return type.value
        } else {
            throw CompilerError("could not find declared type", source = node.source)
        }
    }

    override fun functionType(node: FunctionNode): FunctionType {
        // TODO: better error
        return functionTypes[node.nodeId] ?: throw CompilerError("type of function is unknown: ${node}", source = node.source)
    }

    override fun variableType(node: VariableBindingNode): Type {
        return variableTypes[node.nodeId]!!
    }
}
