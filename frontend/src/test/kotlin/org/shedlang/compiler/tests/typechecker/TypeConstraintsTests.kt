package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.isEquivalentType
import org.shedlang.compiler.tests.isMap
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.tests.unionType
import org.shedlang.compiler.typechecker.CoercionResult
import org.shedlang.compiler.typechecker.coerce
import org.shedlang.compiler.types.*

class TypeConstraintsTests {
    @Test
    fun canCoerceTypeToItself() {
        assertThat(coerce(from = UnitType, to = UnitType), isSuccess())
    }

    @Test
    fun cannotCoerceOneScalarToAnother() {
        assertThat(coerce(from = UnitType, to = IntType), isFailure)
    }

    @Test
    fun whenTypeIsAMemberOfAUnionThenCanCoerceTypeToUnion() {
        val union = unionType("X", listOf(UnitType, IntType))

        assertThat(coerce(from = UnitType, to = union), isSuccess())
        assertThat(coerce(from = IntType, to = union), isSuccess())
        assertThat(coerce(from = StringType, to = union), isFailure)
    }

    @Test
    fun canCoerceUnionToSupersetUnion() {
        val union = unionType("X", listOf(UnitType, IntType))
        val supersetUnion = unionType("Y", listOf(UnitType, IntType, StringType))

        assertThat(coerce(from = union, to = supersetUnion), isSuccess())
    }

    @Test
    fun cannotCoerceUnionToSubsetUnion() {
        val union = unionType("X", listOf(UnitType, IntType, StringType))
        val subsetUnion = unionType("Y", listOf(UnitType, IntType))

        assertThat(coerce(from = union, to = subsetUnion), isFailure)
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
        val result = coerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(BoolType))
        )
        assertThat(result, isSuccess())
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
        val result = coerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(IntType))
        )
        assertThat(result, isFailure)
    }

    @Test
    fun cannotCoerceTypeToFreeParameter() {
        assertThat(
            coerce(from = StringType, to = TypeParameter("T"), parameters = setOf()),
            isFailure
        )
    }

    @Test
    fun coercingTypeToTypeParameterBindsTypeParameterToType() {
        val typeParameter = TypeParameter("T")
        assertThat(
            coerce(from = StringType, to = typeParameter, parameters = setOf(typeParameter)),
            isSuccess(typeParameter to StringType)
        )
    }

    @Test
    fun coercingMultipleTypesToTypeParameterBindingsTypeParameterToUnionOfTypes() {
        val typeParameter = TypeParameter("T")
        assertThat(
            coerce(
                listOf(StringType to typeParameter, IntType to typeParameter),
                parameters = setOf(typeParameter)
            ),
            isSuccess(typeParameter to union(StringType, IntType))
        )
    }

    private fun isSuccess(
        vararg bindings: Pair<TypeParameter, Type>
    ): Matcher<CoercionResult> = cast(has(
        CoercionResult.Success::bindings,
        isMap(*bindings.map({ binding -> binding.first to isEquivalentType(binding.second) }).toTypedArray())
    ))
    private val isFailure = isA<CoercionResult.Failure>()
}
