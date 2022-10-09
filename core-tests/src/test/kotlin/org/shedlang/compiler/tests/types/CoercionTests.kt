package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class CoercionTests {
    private val readEffect = object: Effect {
        override val shortDescription: String
            get() = "!Read"
    }
    private val writeEffect = object: Effect {
        override val shortDescription: String
            get() = "!Write"
    }

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
        val shapeType = shapeType("Box", listOf(field("value", IntType)))
        assertThat(canCoerce(from = shapeType, to = AnyType), equalTo(true))
    }

    @Test
    fun canCoerceNoTypesToNothingType() {
        assertThat(canCoerce(from = UnitType, to = NothingType), equalTo(false))
        val shapeType = shapeType("Box", listOf(field("value", IntType)))
        assertThat(canCoerce(from = shapeType, to = NothingType), equalTo(false))
    }

    @Test
    fun canCoerceNothingTypeToAnyType() {
        assertThat(canCoerce(from = NothingType, to = UnitType), equalTo(true))
        val shapeType = shapeType("Box", listOf(field("value", IntType)))
        assertThat(canCoerce(from = NothingType, to = shapeType), equalTo(true))
    }

    @Test
    fun canCoerceTypeToTypeAlias() {
        assertThat(
            canCoerce(from = IntType, to = typeAlias("Size", IntType)),
            equalTo(true)
        )
    }

    @Test
    fun canCoerceTypeAliasToType() {
        assertThat(
            canCoerce(from = typeAlias("Size", IntType), to = IntType),
            equalTo(true)
        )
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
                from = functionType(namedParameters = mapOf(Identifier("x") to AnyType)),
                to = functionType(namedParameters = mapOf(Identifier("x") to IntType))
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf(Identifier("x") to IntType)),
                to = functionType(namedParameters = mapOf(Identifier("x") to AnyType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceFunctionTypesWithDifferentNamedArguments() {
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf()),
                to = functionType(namedParameters = mapOf(Identifier("x") to IntType))
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf(Identifier("x") to IntType)),
                to = functionType(namedParameters = mapOf())
            ),
            equalTo(false)
        )
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf(Identifier("x") to IntType)),
                to = functionType(namedParameters = mapOf(Identifier("y") to IntType))
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
                from = functionType(effect = readEffect),
                to = functionType(effect = writeEffect)
            ),
            equalTo(false)
        )
    }

    @Test
    fun functionCanBeSubTypeOfOtherFunctionIfEffectIsSubEffect() {
        assertThat(
            canCoerce(
                from = functionType(effect = EmptyEffect),
                to = functionType(effect = readEffect)
            ),
            equalTo(true)
        )
    }

    @Test
    fun functionCannotBeSubTypeOfOtherFunctionIfEffectIsSuperEffect() {
        assertThat(
            canCoerce(
                from = functionType(effect = readEffect),
                to = functionType(effect = EmptyEffect)
            ),
            equalTo(false)
        )
    }

    @Test
    fun canCoerceFromFunctionWithTypeParameters() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            canCoerce(
                from = functionType(
                    typeLevelParameters = listOf(typeParameter),
                    positionalParameters = listOf(typeParameter)
                ),
                to = functionType(
                    typeLevelParameters = listOf(),
                    positionalParameters = listOf(IntType)
                )
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(
                    typeLevelParameters = listOf(),
                    positionalParameters = listOf(IntType)
                ),
                to = functionType(
                    typeLevelParameters = listOf(typeParameter),
                    positionalParameters = listOf(typeParameter)
                )
            ),
            equalTo(false)
        )
    }

    @Test
    fun canCoerceToFunctionWithTypeParameters() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(
            canCoerce(
                from = functionType(
                    typeLevelParameters = listOf(),
                    positionalParameters = listOf(AnyType)
                ),
                to = functionType(
                    typeLevelParameters = listOf(typeParameter),
                    positionalParameters = listOf(typeParameter)
                )
            ),
            equalTo(true)
        )
    }

    @Test
    fun tupleTypeCanBeCoercedToSameTupleType() {
        assertThat(
            canCoerce(
                from = TupleType(listOf(IntType, BoolType)),
                to = TupleType(listOf(IntType, BoolType))
            ),
            equalTo(true)
        )
    }

    @Test
    fun tupleTypeElementsAreCovariant() {
        assertThat(
            canCoerce(
                from = TupleType(listOf(IntType)),
                to = TupleType(listOf(AnyType))
            ),
            equalTo(true)
        )
    }

    @Test
    fun tupleTypeElementsAreNotContravariant() {
        assertThat(
            canCoerce(
                from = TupleType(listOf(AnyType)),
                to = TupleType(listOf(IntType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceTupleTypeToTupleTypeWithDifferentElementTypes() {
        assertThat(
            canCoerce(
                from = TupleType(listOf(IntType, BoolType)),
                to = TupleType(listOf(BoolType, IntType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceTupleTypeToTupleTypeWithExtraElementTypes() {
        assertThat(
            canCoerce(
                from = TupleType(listOf(IntType)),
                to = TupleType(listOf(IntType, BoolType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun cannotCoerceTupleTypeToTupleTypeWithFewerElementTypes() {
        assertThat(
            canCoerce(
                from = TupleType(listOf(IntType, BoolType)),
                to = TupleType(listOf(IntType))
            ),
            equalTo(false)
        )
    }

    @Test
    fun whenTypeIsAMemberOfAUnionThenCanCoerceTypeToUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val union = unionType("Union", members = listOf(member1, member2))

        assertThat(canCoerce(from = member1, to = union), equalTo(true))
        assertThat(canCoerce(from = member2, to = union), equalTo(true))
        assertThat(canCoerce(from = StringType, to = union), equalTo(false))
    }

    @Test
    fun canCoerceUnionToSupersetUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val member3 = shapeType(name = "Member3")

        val union = unionType("Union", members = listOf(member1, member2))
        val supersetUnion = unionType("SupersetUnion", members = listOf(member1, member2, member3))

        assertThat(canCoerce(from = union, to = supersetUnion), equalTo(true))
    }

    @Test
    fun cannotCoerceUnionToSubsetUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val member3 = shapeType(name = "Member3")

        val union = unionType("Union", members = listOf(member1, member2, member3))
        val subsetUnion = unionType("SupersetUnion", members = listOf(member1, member2))

        assertThat(canCoerce(from = union, to = subsetUnion), equalTo(false))
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
        val canCoerce = canCoerce(
            from = applyTypeLevel(shapeType, listOf(BoolType)) as Type,
            to = applyTypeLevel(shapeType, listOf(BoolType)) as Type
        )
        assertThat(canCoerce, equalTo(true))
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
        val canCoerce = canCoerce(
            from = applyTypeLevel(shapeType, listOf(BoolType)) as Type,
            to = applyTypeLevel(shapeType, listOf(IntType)) as Type
        )
        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun canCoerceShapeWithAppliedCovariantTypeArgumentToShapeAppliedWithSuperTypeArgument() {
        val typeParameter = covariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val canCoerce = canCoerce(
            from = applyTypeLevel(shapeType, listOf(BoolType)) as Type,
            to = applyTypeLevel(shapeType, listOf(AnyType)) as Type
        )
        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun cannotCoerceShapeWithAppliedCovariantTypeArgumentToShapeAppliedWithSubTypeArgument() {
        val typeParameter = covariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val canCoerce = canCoerce(
            from = applyTypeLevel(shapeType, listOf(AnyType)) as Type,
            to = applyTypeLevel(shapeType, listOf(BoolType)) as Type
        )
        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun canCoerceShapeWithAppliedContravariantTypeArgumentToShapeAppliedWithSubTypeArgument() {
        val typeParameter = contravariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Sink",
            parameters = listOf(typeParameter),
            fields = listOf()
        )
        val canCoerce = canCoerce(
            from = applyTypeLevel(shapeType, listOf(AnyType)) as Type,
            to = applyTypeLevel(shapeType, listOf(BoolType)) as Type
        )
        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun cannotCoerceShapeWithAppliedContravariantTypeArgumentToShapeAppliedWithSuperTypeArgument() {
        val typeParameter = contravariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Sink",
            parameters = listOf(typeParameter),
            fields = listOf()
        )
        val canCoerce = canCoerce(
            from = applyTypeLevel(shapeType, listOf(BoolType)) as Type,
            to = applyTypeLevel(shapeType, listOf(AnyType)) as Type
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
    fun canCoerceTypeParameterToSupertypeOfMultipleTypes() {
        val tag = tag(listOf("Example"), "X")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))

        val typeParameter = invariantTypeParameter("T")
        val result = coerce(
            constraints = listOf(member1 to typeParameter, member2 to typeParameter),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isSuccess(typeParameter to isUnionType(members = isSequence(
            isEquivalentType(member1),
            isEquivalentType(member2),
        ))))
    }

    @Test
    fun whenTypeParameterHasShapeConstraintThenTypeParameterCannotBeCoercedToNonShape() {
        val shapeId = freshNodeId()
        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
        val result = coerce(
            constraints = listOf(typeParameter to IntType),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isFailure)
    }

    @Test
    fun whenTypeParameterHasShapeConstraintThenTypeParameterCannotBeCoercedToOtherShape() {
        val shapeId = freshNodeId()
        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
        val otherShape = shapeType(shapeId = freshNodeId())
        val result = coerce(
            constraints = listOf(typeParameter to otherShape),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isFailure)
    }

    @Test
    fun whenTypeParameterHasShapeConstraintThenTypeParameterCanBeCoercedToSameShape() {
        val shapeId = freshNodeId()
        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
        val sameShape = shapeType(shapeId = shapeId)
        val result = coerce(
            constraints = listOf(typeParameter to sameShape),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isSuccess(typeParameter to sameInstance(sameShape)))
    }

    @Test
    fun whenTypeParameterHasShapeConstraintThenTypeParameterCannotBeCoercedFromNonShape() {
        val shapeId = freshNodeId()
        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
        val result = coerce(
            constraints = listOf(IntType to typeParameter),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isFailure)
    }

    @Test
    fun whenTypeParameterHasShapeConstraintThenTypeParameterCannotBeCoercedFromOtherShape() {
        val shapeId = freshNodeId()
        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
        val otherShape = shapeType(shapeId = freshNodeId())
        val result = coerce(
            constraints = listOf(otherShape to typeParameter),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isFailure)
    }

    @Test
    fun whenTypeParameterHasShapeConstraintThenTypeParameterCanBeCoercedFromSameShape() {
        val shapeId = freshNodeId()
        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
        val sameShape = shapeType(shapeId = shapeId)
        val result = coerce(
            constraints = listOf(sameShape to typeParameter),
            parameters = setOf(typeParameter)
        )
        assertThat(result, isSuccess(typeParameter to sameInstance(sameShape)))
    }

    @Test
    fun whenTypeParameterIsBoundThenBoundValueHasTypesReplaced() {
        val typeParameter1 = invariantTypeParameter("T1")
        val typeParameter2 = invariantTypeParameter("T2")
        val parametrizedShapeType = parametrizedShapeType(parameters = listOf(invariantTypeParameter("T")))

        val result = coerce(
            constraints = listOf(
                typeParameter2 to IntType,
                typeParameter1 to applyTypeLevel(parametrizedShapeType, listOf(typeParameter2))
            ),
            parameters = setOf(typeParameter1, typeParameter2)
        )

        assertThat(result, isSuccess(
            typeParameter2 to isIntType,
            typeParameter1 to isConstructedType(args = isSequence(isIntType)),
        ))
    }

    @Test
    fun whenTypeParameterIsBoundThenExistingBoundValueHaveTypesReplaced() {
        val typeParameter1 = invariantTypeParameter("T1")
        val typeParameter2 = invariantTypeParameter("T2")
        val parametrizedShapeType = parametrizedShapeType(parameters = listOf(invariantTypeParameter("T")))

        val result = coerce(
            constraints = listOf(
                typeParameter1 to applyTypeLevel(parametrizedShapeType, listOf(typeParameter2)),
                typeParameter2 to IntType
            ),
            parameters = setOf(typeParameter1, typeParameter2)
        )

        assertThat(result, isSuccess(
            typeParameter1 to isConstructedType(args = isSequence(isIntType)),
            typeParameter2 to isIntType,
        ))
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

    @Test
    fun memberEffectIsSubEffectOfUnion() {
        val effect1 = opaqueEffect(name = "A")
        val effect2 = opaqueEffect(name = "B")

        assertThat(
            isSubEffect(subEffect = effect1, superEffect = effectUnion(effect1, effect2)),
            equalTo(true)
        )
    }

    @Test
    fun memberEffectIsNotSuperEffectOfUnion() {
        val effect1 = opaqueEffect(name = "A")
        val effect2 = opaqueEffect(name = "B")

        assertThat(
            isSubEffect(subEffect = effectUnion(effect1, effect2), superEffect = effect1),
            equalTo(false)
        )
    }

    @Test
    fun whenEffectUnionHasSubsetOfEffectsOfOtherEffectUnionThenEffectUnionIsSubEffect() {
        val effect1 = opaqueEffect(name = "A")
        val effect2 = opaqueEffect(name = "B")
        val effect3 = opaqueEffect(name = "C")

        assertThat(
            isSubEffect(subEffect = effectUnion(effect1, effect2), superEffect = effectUnion(effectUnion(effect1, effect2), effect3)),
            equalTo(true)
        )
    }

    @Test
    fun whenEffectParameterIsNotInParameterSetThenCoercingEffectToEffectParameterFails() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(originalParameters = setOf())
        assertThat(
            solver.coerceEffect(from = IoEffect, to = effectParameter),
            equalTo(false)
        )
        assertThat(solver.bindings()[effectParameter], absent())
    }

    @Test
    fun whenEffectParameterIsInParameterSetThenCoercingEffectToEffectParameterBindsEffectParameter() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(originalParameters = setOf(effectParameter))
        assertThat(
            solver.coerceEffect(from = IoEffect, to = effectParameter),
            equalTo(true)
        )
        assertThat(solver.bindings()[effectParameter], present(cast(equalTo(IoEffect))))
    }

    @Test
    fun givenEffectParameterIsBoundWhenCoercingEffectToEffectParameterThenEffectParameterIsTreatedAsBoundEffect() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(originalParameters = setOf(effectParameter))
        solver.coerceEffect(from = IoEffect, to = effectParameter)

        assertThat(
            solver.coerceEffect(from = IoEffect, to = effectParameter),
            equalTo(true)
        )
        assertThat(
            solver.coerceEffect(from = EmptyEffect, to = effectParameter),
            equalTo(true)
        )
        assertThat(
            solver.coerceEffect(from = readEffect, to = effectParameter),
            equalTo(false)
        )
    }

    @Test
    fun whenEffectParameterIsNotInParameterSetThenCoercingEffectParameterToEffectFails() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(originalParameters = setOf())
        assertThat(
            solver.coerceEffect(from = effectParameter, to = IoEffect),
            equalTo(false)
        )
        assertThat(solver.bindings()[effectParameter], absent())
    }

    @Test
    fun whenEffectParameterIsInParameterSetThenCoercingEffectParameterToEffectBindsEffectParameter() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(originalParameters = setOf(effectParameter))
        assertThat(
            solver.coerceEffect(from = effectParameter, to = IoEffect),
            equalTo(true)
        )
        assertThat(solver.bindings()[effectParameter], present(cast(equalTo(IoEffect))))
    }

    @Test
    fun givenEffectParameterIsBoundWhenCoercingEffectParameterToEffectThenEffectParameterIsTreatedAsBoundEffect() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(originalParameters = setOf(effectParameter))
        solver.coerceEffect(from = effectParameter, to = IoEffect)

        assertThat(
            solver.coerceEffect(from = effectParameter, to = IoEffect),
            equalTo(true)
        )
        assertThat(
            solver.coerceEffect(from = effectParameter, to = EmptyEffect),
            equalTo(false)
        )
        assertThat(
            solver.coerceEffect(from = effectParameter, to = readEffect),
            equalTo(false)
        )
    }

    @Test
    fun whenDiscriminatorCanBeFoundThenTypeIsSubTypeOfCastable() {
        val tag = tag(listOf("Example"), "Union")
        val memberType = shapeType(name = "Member", tagValue = tagValue(tag, "B"))
        val unionType = unionType(name = "Union", tag = tag, members = listOf(memberType))

        val canCoerce = canCoerce(from = unionType, to = castableType(memberType))

        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun whenDiscriminatorCannotBeFoundThenTypeIsNotSubTypeOfCastable() {
        val unionTag = tag(listOf("Example"), "Union")
        val memberType = shapeType(name = "Member", tagValue = tagValue(unionTag, "B"))
        val unionType = unionType(name = "Union", tag = unionTag, members = listOf(memberType))
        val otherUnionTag = tag(listOf("Example"), "OtherUnion")
        val otherMemberType = shapeType(name = "Member", tagValue = tagValue(otherUnionTag, "B"))

        val canCoerce = canCoerce(from = unionType, to = castableType(otherMemberType))

        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun metaTypesCanBeCoercedToSameMetaType() {
        val canCoerce = canCoerce(from = IntMetaType, to = IntMetaType)

        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun metaTypesCannotBeCoercedToUnrelatedMetaType() {
        val canCoerce = canCoerce(from = IntMetaType, to = BoolMetaType)

        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun metaTypesCannotBeCoercedToMetaTypeOfSubType() {
        val canCoerce = canCoerce(from = IntMetaType, to = NothingMetaType)

        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun metaTypesCannotBeCoercedToMetaTypeOfSuperType() {
        val canCoerce = canCoerce(from = IntMetaType, to = AnyMetaType)

        assertThat(canCoerce, equalTo(false))
    }

    private fun assertSymmetricCoercion(parameters: Set<TypeLevelParameter>, from: Type, to: Type, matcher: Matcher<CoercionResult>) {
        val result1 = coerce(
            parameters = parameters,
            from = from,
            to = to,
        )

        assertThat(result1, matcher)

        val result2 = coerce(
            parameters = parameters,
            from = to,
            to = from,
        )

        assertThat(result2, matcher)
    }

    private fun isSuccess(vararg bindings: Pair<TypeLevelParameter, Matcher<TypeLevelValue>>): Matcher<CoercionResult> {
        return cast(has(CoercionResult.Success::bindings, isMap(*bindings)))
    }

    private val isFailure = isA<CoercionResult.Failure>()
}
