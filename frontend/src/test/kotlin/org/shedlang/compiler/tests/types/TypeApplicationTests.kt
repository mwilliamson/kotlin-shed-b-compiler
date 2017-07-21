package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class TypeApplicationTests {
    @Test
    fun applyingTypeToShapeUpdatesTypeArguments() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val shape = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            shapeType("Pair", fields = mapOf())
        )
        assertThat(
            applyType(shape, listOf(BoolType, IntType)),
            isShapeType(
                name = equalTo("Pair"),
                typeArguments = isSequence(isBoolType, isIntType)
            )
        )
    }

    @Test
    fun applyingTypeToShapeReplacesTypeParameters() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val shape = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            shapeType("Pair", fields = mapOf(
                "first" to typeParameter1,
                "second" to typeParameter2
            ))
        )
        assertThat(
            applyType(shape, listOf(BoolType, IntType)),
            isShapeType(fields = listOf(
                "first" to isBoolType,
                "second" to isIntType
            ))
        )
    }

    @Test
    fun applyingTypeToUnionUpdatesTypeArguments() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val union = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            unionType("Either", members = listOf())
        )
        assertThat(
            applyType(union, listOf(BoolType, IntType)),
            isUnionType(
                name = equalTo("Either"),
                typeArguments = isSequence(isBoolType, isIntType)
            )
        )
    }

    @Test
    fun applyingTypeToUnionReplacesTypeParameters() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val union = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            unionType("Either", members = listOf(typeParameter1, typeParameter2))
        )
        assertThat(
            applyType(union, listOf(BoolType, IntType)),
            isUnionType(members = isSequence(isBoolType, isIntType))
        )
    }

    @Test
    fun applyingTypeToUnionReplacesTypeParametersInMembers() {
        val shapeTypeParameter = TypeParameter("U")
        val shapeType = TypeFunction(
            listOf(shapeTypeParameter),
            shapeType("Shape")
        )

        val unionTypeParameter = TypeParameter("T")
        val union = TypeFunction(
            listOf(unionTypeParameter),
            unionType("Union", members = listOf(applyType(shapeType, listOf(unionTypeParameter))))
        )

        assertThat(
            applyType(union, listOf(BoolType)),
            isUnionType(members = isSequence(
                isEquivalentType(applyType(shapeType, listOf(BoolType)))
            ))
        )
    }

    @Test
    fun applyingTypeToShapeReplacesTypeParametersInFields() {
        val unionTypeParameter = TypeParameter("T")
        val union = TypeFunction(
            listOf(unionTypeParameter),
            unionType("Union", members = listOf(unionTypeParameter))
        )

        val shapeTypeParameter = TypeParameter("U")
        val shapeType = TypeFunction(
            listOf(shapeTypeParameter),
            shapeType("Shape", fields = mapOf("value" to applyType(union, listOf(shapeTypeParameter))))
        )

        assertThat(
            applyType(shapeType, listOf(BoolType)),
            isShapeType(fields = listOf(
                "value" to isEquivalentType(applyType(union, listOf(BoolType)))
            ))
        )
    }
}
