package org.shedlang.compiler.tests.typechecker

import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.ModuleStatementNode
import org.shedlang.compiler.ast.ReferenceNode
import org.shedlang.compiler.ast.VariableBindingNode
import org.shedlang.compiler.typechecker.TypeCheckPhase
import org.shedlang.compiler.typechecker.TypeContext
import org.shedlang.compiler.typechecker.typeCheckExpression
import org.shedlang.compiler.typechecker.typeCheckModuleStatement
import org.shedlang.compiler.types.Type

internal fun captureTypes(
    expression: ExpressionNode,
    references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
    referenceTypes: Map<ReferenceNode, Type> = mapOf(),
    types: Map<VariableBindingNode, Type> = mapOf()
): TypesMap {
    val expressionTypes = mutableMapOf<Int, Type>()
    val typeContext = typeContext(
        expressionTypes = expressionTypes,
        referenceTypes = referenceTypes,
        references = references,
        types = types
    )

    typeCheckExpression(expression, typeContext)
    typeContext.undefer()

    return TypesMap(
        discriminators = mapOf(),
        expressionTypes = expressionTypes,
        functionTypes = mapOf(),
        targetTypes = mapOf(),
        variableTypes = mapOf()
    )
}

internal fun typeCheckModuleStatementAllPhases(node: ModuleStatementNode, context: TypeContext) {
    val steps = typeCheckModuleStatement(node)

    for (phase in TypeCheckPhase.values()) {
        steps.run(phase, context)
    }
}
