package org.shedlang.compiler.stackir

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.Type

fun defineShapeFieldGet(shapeType: Type, fieldName: Identifier): DefineFunction {
    val parameter = DefineFunction.Parameter(Identifier("receiver"), freshNodeId())

    return DefineFunction(
        name = "get",
        bodyInstructions = persistentListOf(
            LocalLoad(parameter),
            FieldAccess(fieldName, receiverType = shapeType),
            Return
        ),
        positionalParameters = listOf(parameter),
        namedParameters = listOf()
    )
}

fun defineShapeFieldUpdate(shapeType: Type, fieldName: Identifier): DefineFunction {
    val fieldValueParameter = DefineFunction.Parameter(Identifier("fieldValue"), freshNodeId())
    val baseParameter = DefineFunction.Parameter(Identifier("base"), freshNodeId())

    return DefineFunction(
        name = "update",
        bodyInstructions = persistentListOf(
            LocalLoad(baseParameter),
            LocalLoad(fieldValueParameter),
            FieldUpdate(fieldName, receiverType = shapeType),
            Return
        ),
        positionalParameters = listOf(fieldValueParameter, baseParameter),
        namedParameters = listOf()
    )
}
