package org.shedlang.compiler.stackir

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.types.Type

fun defineShapeFieldGet(shapeType: Type, fieldName: Identifier): DefineFunction {
    val getParameter = DefineFunction.Parameter(Identifier("receiver"), freshNodeId())

    return DefineFunction(
        name = "get",
        bodyInstructions = persistentListOf(
            LocalLoad(getParameter),
            FieldAccess(fieldName, receiverType = shapeType),
            Return
        ),
        positionalParameters = listOf(getParameter),
        namedParameters = listOf()
    )
}
