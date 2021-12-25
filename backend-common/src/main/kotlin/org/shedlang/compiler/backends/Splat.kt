package org.shedlang.compiler.backends

import org.shedlang.compiler.Types
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.FieldArgumentNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.Type

fun fieldArgumentsToFieldsProvided(
    fieldArguments: List<FieldArgumentNode>,
    typeOfExpression: (expression: ExpressionNode) -> Type,
): List<LinkedHashSet<Identifier>> {
    val providesFields = mutableListOf<LinkedHashSet<Identifier>>()

    fieldArguments.forEach { fieldArgument ->
        val argumentProvidesFields = when (fieldArgument) {
            is FieldArgumentNode.Named -> {
                setOf(fieldArgument.name)
            }
            is FieldArgumentNode.Splat -> {
                val argType = typeOfExpression(fieldArgument.expression) as ShapeType
                argType.fields.keys
            }
        }
        for (previousArgumentProvidesFields in providesFields) {
            previousArgumentProvidesFields.removeAll(argumentProvidesFields)
        }
        providesFields.add(LinkedHashSet(argumentProvidesFields))
    }

    return providesFields
}
