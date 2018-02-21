package org.shedlang.compiler.tests.typechecker

import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.ReferenceNode
import org.shedlang.compiler.ast.VariableBindingNode
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.Type

internal fun captureType(
    expression: ExpressionNode,
    capture: ExpressionNode,
    references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
    referenceTypes: Map<ReferenceNode, Type> = mapOf(),
    types: Map<VariableBindingNode, Type> = mapOf()
): Type {
    val expressionTypes = mutableMapOf<Int, Type>()
    val typeContext = typeContext(
        expressionTypes = expressionTypes,
        referenceTypes = referenceTypes,
        references = references,
        types = types
    )

    typeCheck(expression, typeContext)
    typeContext.undefer()

    return expressionTypes[capture.nodeId]!!
}
