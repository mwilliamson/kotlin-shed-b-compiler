package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class TypeApplicationTests {
    @Test
    fun applyingTypeToShapeRenamesShape() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val shape = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            shapeType("Pair", fields = mapOf())
        )
        assertThat(
            applyType(shape, listOf(BoolType, IntType)),
            isShapeType(name = equalTo("Pair[Bool, Int]"), fields = listOf())
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
    fun applyingTypeToUnionRenamesUnion() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val union = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            unionType("Either", members = listOf())
        )
        assertThat(
            applyType(union, listOf(BoolType, IntType)),
            isUnionType(name = equalTo("Either[Bool, Int]"))
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
}
