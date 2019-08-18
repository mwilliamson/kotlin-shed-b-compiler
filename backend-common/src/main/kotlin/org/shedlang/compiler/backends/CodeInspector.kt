package org.shedlang.compiler.backends

import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.CallBaseNode
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.ReferenceNode
import org.shedlang.compiler.ast.VariableBindingNode
import org.shedlang.compiler.types.Type

interface CodeInspector {
    fun isCast(node: CallBaseNode): Boolean
    fun resolve(node: ReferenceNode): VariableBindingNode
    fun typeOfExpression(node: ExpressionNode): Type
}

class ModuleCodeInspector(private val module: Module.Shed): CodeInspector {
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
    private val expressionTypes: Map<ExpressionNode, Type> = mapOf(),
    private val references: Map<ReferenceNode, VariableBindingNode> = mapOf()
): CodeInspector {
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
