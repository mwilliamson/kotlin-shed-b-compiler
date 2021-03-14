package org.shedlang.compiler.backends

import org.shedlang.compiler.Types
import org.shedlang.compiler.ast.FieldArgumentNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.ShapeType

fun fieldArgumentsToFieldsProvided(fieldArguments: List<FieldArgumentNode>, types: Types): List<LinkedHashSet<Identifier>> {
    val providesFields = mutableListOf<LinkedHashSet<Identifier>>()

    fieldArguments.forEachIndexed { fieldArgumentIndex, fieldArgument ->
        val argumentProvidesFields = when (fieldArgument) {
            is FieldArgumentNode.Named -> {
                setOf(fieldArgument.name)
            }
            is FieldArgumentNode.Splat -> {
                val argType = types.typeOfExpression(fieldArgument.expression) as ShapeType
                argType.populatedFieldNames
            }
        }
        for (previousArgumentProvidesFields in providesFields) {
            previousArgumentProvidesFields.removeAll(argumentProvidesFields)
        }
        providesFields.add(LinkedHashSet(argumentProvidesFields))
    }

    return providesFields
}
