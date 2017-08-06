package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.tests.unionType
import org.shedlang.compiler.typechecker.canCoerce
import org.shedlang.compiler.types.*

class CoercionTests {
    @Test
    fun canCoerceTypeToItself() {
        assertThat(canCoerce(from = UnitType, to = UnitType), equalTo(true))
    }

    @Test
    fun cannotCoerceOneScalarToAnother() {
        assertThat(canCoerce(from = UnitType, to = IntType), equalTo(false))
    }

    @Test
    fun canCoerceAllTypesToAnyType() {
        assertThat(canCoerce(from = UnitType, to = AnyType), equalTo(true))
        val shapeType = shapeType("Box", mapOf("value" to IntType))
        assertThat(canCoerce(from = shapeType, to = AnyType), equalTo(true))
    }

    @Test
    fun canCoerceNoTypesToNothingType() {
        assertThat(canCoerce(from = UnitType, to = NothingType), equalTo(false))
        val shapeType = shapeType("Box", mapOf("value" to IntType))
        assertThat(canCoerce(from = shapeType, to = NothingType), equalTo(false))
    }

    @Test
    fun canCoerceNothingTypeToAnyType() {
        assertThat(canCoerce(from = NothingType, to = UnitType), equalTo(true))
        val shapeType = shapeType("Box", mapOf("value" to IntType))
        assertThat(canCoerce(from = NothingType, to = shapeType), equalTo(true))
    }

    @Test
    fun whenTypeIsAMemberOfAUnionThenCanCoerceTypeToUnion() {
        val union = unionType("X", listOf(UnitType, IntType))

        assertThat(canCoerce(from = UnitType, to = union), equalTo(true))
        assertThat(canCoerce(from = IntType, to = union), equalTo(true))
        assertThat(canCoerce(from = StringType, to = union), equalTo(false))
    }

    @Test
    fun canCoerceUnionToSupersetUnion() {
        val union = unionType("X", listOf(UnitType, IntType))
        val supersetUnion = unionType("Y", listOf(UnitType, IntType, StringType))

        assertThat(canCoerce(from = union, to = supersetUnion), equalTo(true))
    }

    @Test
    fun cannotCoerceUnionToSubsetUnion() {
        val union = unionType("X", listOf(UnitType, IntType, StringType))
        val subsetUnion = unionType("Y", listOf(UnitType, IntType))

        assertThat(canCoerce(from = union, to = subsetUnion), equalTo(false))
    }

    @Test
    fun canCoerceShapeWithAppliedTypeArgumentsToShapeAppliedWithSameTypeArguments() {
        val typeParameter = TypeParameter("T")
        val shapeType = TypeFunction(
            listOf(typeParameter),
            shapeType("Box", fields = mapOf(
                "value" to typeParameter
            ))
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(BoolType))
        )
        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun cannotCoerceShapeWithAppliedTypeArgumentsToShapeAppliedWithDifferentTypeArguments() {
        val typeParameter = TypeParameter("T")
        val shapeType = TypeFunction(
            listOf(typeParameter),
            shapeType("Box", fields = mapOf(
                "value" to typeParameter
            ))
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(IntType))
        )
        assertThat(canCoerce, equalTo(false))
    }
}
