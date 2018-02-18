package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.frontend.types.applyType
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.CoercionResult
import org.shedlang.compiler.typechecker.canCoerce
import org.shedlang.compiler.typechecker.coerce
import org.shedlang.compiler.typechecker.isSubEffect
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
                from = functionType(positionalParameters = listOf(AnyType)),
                to = functionType(positionalParameters = listOf(IntType))
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(positionalParameters = listOf(IntType)),
                to = functionType(positionalParameters = listOf(AnyType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceFunctionTypesWithDifferentNumberOfPositionalArguments() {
        assertThat(
            canCoerce(
                from = functionType(positionalParameters = listOf()),
                to = functionType(positionalParameters = listOf(IntType))
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(positionalParameters = listOf(IntType)),
                to = functionType(positionalParameters = listOf())
            ),
            equalTo(false)
        )
    }

    @Test
    fun functionTypesAreContravariantInNamedArgumentType() {
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf("x" to AnyType)),
                to = functionType(namedParameters = mapOf("x" to IntType))
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf("x" to IntType)),
                to = functionType(namedParameters = mapOf("x" to AnyType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceFunctionTypesWithDifferentNamedArguments() {
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf()),
                to = functionType(namedParameters = mapOf("x" to IntType))
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf("x" to IntType)),
                to = functionType(namedParameters = mapOf())
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf("x" to IntType)),
                to = functionType(namedParameters = mapOf("y" to IntType))
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
        val readEffect = object: Effect {
            override val shortDescription: String
                get() = "!Read"
        }
        val writeEffect = object: Effect {
            override val shortDescription: String
                get() = "!Write"
        }

        assertThat(
            canCoerce(
                from = functionType(effect = readEffect),
                to = functionType(effect = writeEffect)
            ),
            equalTo(false)
        )
    }

    @Test
    fun functionTypeWithTypeParametersNeverMatch() {
        assertThat(
            canCoerce(
                from = functionType(staticParameters = listOf(invariantTypeParameter("T"))),
                to = functionType(staticParameters = listOf(invariantTypeParameter("T")))
            ),
            equalTo(false)
        )
    }

    @Test
    fun whenTypeIsAMemberOfAUnionThenCanCoerceTypeToUnion() {
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val union = unionType("Union", members = listOf(member1, member2), tagField = tagField)

        assertThat(canCoerce(from = member1, to = union), equalTo(true))
        assertThat(canCoerce(from = member2, to = union), equalTo(true))
        assertThat(canCoerce(from = StringType, to = union), equalTo(false))
    }

    @Test
    fun canCoerceUnionToSupersetUnion() {
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val member3 = shapeType(name = "Member3", tagValue = TagValue(tagField, freshNodeId()))

        val union = unionType("Union", members = listOf(member1, member2), tagField = tagField)
        val supersetUnion = unionType("SupersetUnion", members = listOf(member1, member2, member3), tagField = tagField)

        assertThat(canCoerce(from = union, to = supersetUnion), equalTo(true))
    }

    @Test
    fun cannotCoerceUnionToSubsetUnion() {
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val member3 = shapeType(name = "Member3", tagValue = TagValue(tagField, freshNodeId()))

        val union = unionType("Union", members = listOf(member1, member2, member3), tagField = tagField)
        val subsetUnion = unionType("SupersetUnion", members = listOf(member1, member2), tagField = tagField)

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

    @Test
    fun canCoerceShapeWithAppliedCovariantTypeArgumentToShapeAppliedWithSuperTypeArgument() {
        val typeParameter = covariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = mapOf(
                "value" to typeParameter
            )
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(AnyType))
        )
        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun cannotCoerceShapeWithAppliedCovariantTypeArgumentToShapeAppliedWithSubTypeArgument() {
        val typeParameter = covariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = mapOf(
                "value" to typeParameter
            )
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(AnyType)),
            to = applyType(shapeType, listOf(BoolType))
        )
        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun canCoerceShapeWithAppliedContravariantTypeArgumentToShapeAppliedWithSubTypeArgument() {
        val typeParameter = contravariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Sink",
            parameters = listOf(typeParameter),
            fields = mapOf()
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(AnyType)),
            to = applyType(shapeType, listOf(BoolType))
        )
        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun cannotCoerceShapeWithAppliedContravariantTypeArgumentToShapeAppliedWithSuperTypeArgument() {
        val typeParameter = contravariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Sink",
            parameters = listOf(typeParameter),
            fields = mapOf()
        )
        val canCoerce = canCoerce(
            from = applyType(shapeType, listOf(BoolType)),
            to = applyType(shapeType, listOf(AnyType))
        )
        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun canCoerceTypeParameterToSubtypeOfType() {
        val typeParameter = invariantTypeParameter("T")
        val result = coerce(
            constraints = listOf(typeParameter to IntType),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isSuccess(typeParameter to isIntType))
    }

    @Test
    fun cannotCoerceTypeParameterToSubtypeOfMultipleTypes() {
        val typeParameter = invariantTypeParameter("T")
        val result = coerce(
            constraints = listOf(typeParameter to IntType, typeParameter to BoolType),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isFailure)
    }

    @Test
    fun canCoerceTypeParameterToSupertypeOfType() {
        val typeParameter = invariantTypeParameter("T")
        val result = coerce(
            constraints = listOf(IntType to typeParameter),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isSuccess(typeParameter to isIntType))
    }

    @Test
    fun canCoerceTypeParameterToSupertypeOfMultipleTypesWithSameTag() {
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))

        val typeParameter = invariantTypeParameter("T")
        val result = coerce(
            constraints = listOf(member1 to typeParameter, member2 to typeParameter),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isSuccess(typeParameter to isUnionType(members = isSequence(cast(sameInstance(member1)), cast(sameInstance(member2))))))
    }

    @Test
    fun effectIsSubEffectOfItself() {
        assertThat(
            isSubEffect(subEffect = IoEffect, superEffect = IoEffect),
            equalTo(true)
        )
    }

    @Test
    fun emptyEffectIsSubEffectOfOtherEffects() {
        assertThat(
            isSubEffect(subEffect = EmptyEffect, superEffect = IoEffect),
            equalTo(true)
        )
    }

    @Test
    fun emptyEffectIsNotSuperEffectOfOtherEffects() {
        assertThat(
            isSubEffect(subEffect = IoEffect, superEffect = EmptyEffect),
            equalTo(false)
        )
    }

    private fun isSuccess(vararg bindings: Pair<TypeParameter, Matcher<Type>>): Matcher<CoercionResult> {
        return cast(has(CoercionResult.Success::bindings, isMap(*bindings)))
    }

    private val isFailure = isA<CoercionResult.Failure>()
}
