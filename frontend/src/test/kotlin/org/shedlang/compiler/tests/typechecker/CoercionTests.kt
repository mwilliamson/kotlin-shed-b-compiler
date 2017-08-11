package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.parametrizedShapeType
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
    fun functionTypesAreContravariantInPositionalArgumentType() {
        assertThat(
            canCoerce(
                from = functionType(positionalArguments = listOf(AnyType)),
                to = functionType(positionalArguments = listOf(IntType))
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(positionalArguments = listOf(IntType)),
                to = functionType(positionalArguments = listOf(AnyType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceFunctionTypesWithDifferentNumberOfPositionalArguments() {
        assertThat(
            canCoerce(
                from = functionType(positionalArguments = listOf()),
                to = functionType(positionalArguments = listOf(IntType))
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(positionalArguments = listOf(IntType)),
                to = functionType(positionalArguments = listOf())
            ),
            equalTo(false)
        )
    }

    @Test
    fun functionTypesAreContravariantInNamedArgumentType() {
        assertThat(
            canCoerce(
                from = functionType(namedArguments = mapOf("x" to AnyType)),
                to = functionType(namedArguments = mapOf("x" to IntType))
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(namedArguments = mapOf("x" to IntType)),
                to = functionType(namedArguments = mapOf("x" to AnyType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceFunctionTypesWithDifferentNamedArguments() {
        assertThat(
            canCoerce(
                from = functionType(namedArguments = mapOf()),
                to = functionType(namedArguments = mapOf("x" to IntType))
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(namedArguments = mapOf("x" to IntType)),
                to = functionType(namedArguments = mapOf())
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(namedArguments = mapOf("x" to IntType)),
                to = functionType(namedArguments = mapOf("y" to IntType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun functionTypesAreCovariantInReturnType() {
        assertThat(
            canCoerce(
                from = functionType(returns = IntType),
                to = functionType(returns = AnyType)
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(returns = AnyType),
                to = functionType(returns = IntType)
            ),
            equalTo(false)
        )
    }

    @Test
    fun functionTypeEffectsMustMatch() {
        assertThat(
            canCoerce(
                from = functionType(effects = setOf()),
                to = functionType(effects = setOf(IoEffect))
            ),
            equalTo(false)
        )
    }

    @Test
    fun functionTypeWithTypeParametersNeverMatch() {
        assertThat(
            canCoerce(
                from = functionType(typeParameters = listOf(invariantTypeParameter("T"))),
                to = functionType(typeParameters = listOf(invariantTypeParameter("T")))
            ),
            equalTo(false)
        )
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
        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = mapOf(
                "value" to typeParameter
            )
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(BoolType))
        )
        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun cannotCoerceShapeWithAppliedTypeArgumentsToShapeAppliedWithDifferentTypeArguments() {
        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = mapOf(
                "value" to typeParameter
            )
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(IntType))
        )
        assertThat(canCoerce, equalTo(false))
    }
}
