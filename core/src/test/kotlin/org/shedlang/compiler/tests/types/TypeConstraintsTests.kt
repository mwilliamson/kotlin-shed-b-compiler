package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
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
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val union = unionType("Union", members = listOf(member1, member2))

        assertThat(coerce(from = member1, to = union), isSuccess())
        assertThat(coerce(from = member2, to = union), isSuccess())
        assertThat(coerce(from = StringType, to = union), isFailure)
    }

    @Test
    fun canCoerceUnionToSupersetUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val member3 = shapeType(name = "Member3")

        val union = unionType("Union", members = listOf(member1, member2))
        val supersetUnion = unionType("SupersetUnion", members = listOf(member1, member2, member3))

        assertThat(coerce(from = union, to = supersetUnion), isSuccess())
    }

    @Test
    fun cannotCoerceUnionToSubsetUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val member3 = shapeType(name = "Member3")

        val union = unionType("Union", members = listOf(member1, member2, member3))
        val subsetUnion = unionType("SubsetUnion", members = listOf(member1, member2))

        assertThat(coerce(from = union, to = subsetUnion), isFailure)
    }

    @Test
    fun canCoerceShapeWithAppliedTypeArgumentsToShapeAppliedWithSameTypeArguments() {
        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val result = coerce(
            from = applyStatic(shapeType, listOf(BoolType)),
            to = applyStatic(shapeType, listOf(BoolType))
        )
        assertThat(result, isSuccess())
    }

    @Test
    fun cannotCoerceShapeWithAppliedTypeArgumentsToShapeAppliedWithDifferentTypeArguments() {
        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val result = coerce(
            from = applyStatic(shapeType, listOf(BoolType)),
            to = applyStatic(shapeType, listOf(IntType))
        )
        assertThat(result, isFailure)
    }

    @Test
    fun cannotCoerceTypeToFreeParameter() {
        assertThat(
            coerce(from = StringType, to = invariantTypeParameter("T"), parameters = setOf()),
            isFailure
        )
    }

    @Test
    fun coercingTypeToTypeParameterBindsTypeParameterToType() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(from = StringType, to = typeParameter, parameters = setOf(typeParameter)),
            isSuccess(typeParameter to StringType)
        )
    }

    @Test
    fun coercingMultipleTypesToTypeParameterBindsTypeParameterToUnionOfTypes() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")

        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(
                listOf(member1 to typeParameter, member2 to typeParameter),
                parameters = setOf(typeParameter)
            ),
            isSuccess(typeParameter to union(member1, member2))
        )
    }

    @Test
    fun coercingTypeParameterToTypeBindsTypeParameterToType() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(
                listOf(typeParameter to StringType),
                parameters = setOf(typeParameter)
            ),
            isSuccess(typeParameter to StringType)
        )
    }

    @Test
    fun whenUnionIsCoercedToTypeParameterThenTypeParameterIsBoundToUnion() {
        val typeParameter = invariantTypeParameter("T")
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val union = unionType("Union", members = listOf(member1, member2))

        val subType = functionType(positionalParameters = listOf(union))
        val superType = functionType(positionalParameters = listOf(typeParameter))

        assertThat(
            coerce(
                listOf(subType to superType),
                parameters = setOf(typeParameter)
            ),
            isSuccess(typeParameter to union)
        )
    }

    @Test
    fun whenTypeParameterIsBoundToUnionThenTypeParameterCanBeCoercedToUnion() {
        val typeParameter = invariantTypeParameter("T")
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val union = unionType("Union", members = listOf(member1, member2))

        assertThat(
            coerce(
                listOf(union to typeParameter, typeParameter to union),
                parameters = setOf(typeParameter)
            ),
            isSuccess(typeParameter to union)
        )
    }

    @Test
    fun whenTypeParameterIsCoercedToTypeThanCanCoerceTypeParameterToSameType() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(
                listOf(typeParameter to StringType, typeParameter to StringType),
                parameters = setOf(typeParameter)
            ),
            isSuccess(typeParameter to StringType)
        )
    }

    @Test
    fun whenTypeParameterIsCoercedToTypeThenCanCoerceSameTypeToTypeParameter() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(
                listOf(typeParameter to StringType, StringType to typeParameter),
                parameters = setOf(typeParameter)
            ),
            isSuccess(typeParameter to StringType)
        )
    }

    @Test
    fun canCoerceBoundTypeParameterToOtherTypeParameterBoundToSuperType() {
        val firstTypeParameter = invariantTypeParameter("T")
        val secondTypeParameter = invariantTypeParameter("U")
        assertThat(
            coerce(
                listOf(
                    firstTypeParameter to functionType(returns = StringType),
                    secondTypeParameter to functionType(returns = AnyType),
                    functionType(returns = firstTypeParameter) to functionType(returns = secondTypeParameter)
                ),
                parameters = setOf(firstTypeParameter, secondTypeParameter)
            ),
            isSuccess(
                firstTypeParameter to functionType(returns = StringType),
                secondTypeParameter to functionType(returns = AnyType)
            )
        )
    }

    @Test
    fun cannotCoerceTypeParametersToDistinctTypes() {
        // TODO: could be the bottom type instead
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(
                listOf(typeParameter to StringType, typeParameter to IntType),
                parameters = setOf(typeParameter)
            ),
            isFailure
        )
    }

    @Test
    fun whenTypeParameterIsCoercedToTypeThanCannotCoerceDistinctTypeToTypeParameter() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(
                listOf(typeParameter to StringType, IntType to typeParameter),
                parameters = setOf(typeParameter)
            ),
            isFailure
        )
    }

    @Test
    fun whenTypeIsCoercedToTypeParameterThenCannotCoerceTypeParameterToDistinctType() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            coerce(
                listOf(IntType to typeParameter, typeParameter to StringType),
                parameters = setOf(typeParameter)
            ),
            isFailure
        )
    }

    @Test
    fun coercingEffectToEffectParameterBindsParameter() {
        val parameter = effectParameter("E")
        val constraints = TypeConstraintSolver(parameters = setOf(parameter))
        assertThat(constraints.coerceEffect(from = IoEffect, to = parameter), equalTo(true))
        assertThat(constraints.effectBindings, isMap(parameter to cast(equalTo(IoEffect))))
    }

    @Test
    fun coercingSameEffectToEffectParameterBindsParameter() {
        val parameter = effectParameter("E")
        val constraints = TypeConstraintSolver(parameters = setOf(parameter))
        assertThat(constraints.coerceEffect(from = IoEffect, to = parameter), equalTo(true))
        assertThat(constraints.coerceEffect(from = IoEffect, to = parameter), equalTo(true))
        assertThat(constraints.effectBindings, isMap(parameter to cast(equalTo(IoEffect))))
    }

    private fun isSuccess(
        vararg bindings: Pair<TypeParameter, Type>
    ): Matcher<CoercionResult> = cast(has(
        CoercionResult.Success::bindings,
        isMap(*bindings.map({ binding -> binding.first to isEquivalentType(binding.second) }).toTypedArray())
    ))
    private val isFailure = isA<CoercionResult.Failure>()
}
