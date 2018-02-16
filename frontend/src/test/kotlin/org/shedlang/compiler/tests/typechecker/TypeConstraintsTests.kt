package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
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
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val union = unionType("Union", members = listOf(member1, member2), tagField = tagField)

        assertThat(coerce(from = member1, to = union), isSuccess())
        assertThat(coerce(from = member2, to = union), isSuccess())
        assertThat(coerce(from = StringType, to = union), isFailure)
    }

    @Test
    fun canCoerceUnionToSupersetUnion() {
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val member3 = shapeType(name = "Member3", tagValue = TagValue(tagField, freshNodeId()))

        val union = unionType("Union", members = listOf(member1, member2), tagField = tagField)
        val supersetUnion = unionType("SupersetUnion", members = listOf(member1, member2, member3), tagField = tagField)

        assertThat(coerce(from = union, to = supersetUnion), isSuccess())
    }

    @Test
    fun cannotCoerceUnionToSubsetUnion() {
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val member3 = shapeType(name = "Member3", tagValue = TagValue(tagField, freshNodeId()))

        val union = unionType("Union", members = listOf(member1, member2, member3), tagField = tagField)
        val subsetUnion = unionType("SubsetUnion", members = listOf(member1, member2), tagField = tagField)

        assertThat(coerce(from = union, to = subsetUnion), isFailure)
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
        val result = coerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(BoolType))
        )
        assertThat(result, isSuccess())
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
        val result = coerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(IntType))
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
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))

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
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val union = unionType("Union", members = listOf(member1, member2), tagField = tagField)

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
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val union = unionType("Union", members = listOf(member1, member2), tagField = tagField)

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

    private fun isSuccess(
        vararg bindings: Pair<TypeParameter, Type>
    ): Matcher<CoercionResult> = cast(has(
        CoercionResult.Success::bindings,
        isMap(*bindings.map({ binding -> binding.first to isEquivalentType(binding.second) }).toTypedArray())
    ))
    private val isFailure = isA<CoercionResult.Failure>()
}
