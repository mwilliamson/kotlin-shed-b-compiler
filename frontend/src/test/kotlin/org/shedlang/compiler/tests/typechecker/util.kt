package org.shedlang.compiler.tests.typechecker

import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.*
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

internal fun typeCheckFunctionStatementAllPhases(node: FunctionStatementNode, context: TypeContext): Type {
    val steps = typeCheckFunctionStatementSteps(node)

    for (phase in TypeCheckPhase.values()) {
        steps.run(phase, context)
    }

    return steps.type()
}
