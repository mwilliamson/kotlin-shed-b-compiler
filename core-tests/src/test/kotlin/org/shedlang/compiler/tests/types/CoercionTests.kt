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
    fun canCoerceAllTypesToTopType() {
        assertThat(canCoerce(from = UnitType, to = TopType), equalTo(true))
        val shapeType = shapeType("Box", listOf(field("value", IntType)))
        assertThat(canCoerce(from = shapeType, to = TopType), equalTo(true))
    }

    @Test
    fun canCoerceNoTypesToBottomType() {
        assertThat(canCoerce(from = UnitType, to = BottomType), equalTo(false))
        val shapeType = shapeType("Box", listOf(field("value", IntType)))
        assertThat(canCoerce(from = shapeType, to = BottomType), equalTo(false))
    }

    @Test
    fun canCoerceBottomTypeToAnyType() {
        assertThat(canCoerce(from = BottomType, to = UnitType), equalTo(true))
        val shapeType = shapeType("Box", listOf(field("value", IntType)))
        assertThat(canCoerce(from = BottomType, to = shapeType), equalTo(true))
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
                from = functionType(positionalParameters = listOf(TopType)),
                to = functionType(positionalParameters = listOf(IntType))
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(positionalParameters = listOf(IntType)),
                to = functionType(positionalParameters = listOf(TopType))
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
                from = functionType(namedParameters = mapOf(Identifier("x") to TopType)),
                to = functionType(namedParameters = mapOf(Identifier("x") to IntType))
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(namedParameters = mapOf(Identifier("x") to IntType)),
                to = functionType(namedParameters = mapOf(Identifier("x") to TopType))
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
                to = functionType(returns = TopType)
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(returns = TopType),
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
                    staticParameters = listOf(typeParameter),
                    positionalParameters = listOf(typeParameter)
                ),
                to = functionType(
                    staticParameters = listOf(),
                    positionalParameters = listOf(IntType)
                )
            ),
            equalTo(true)
        )
        assertThat(
            canCoerce(
                from = functionType(
                    staticParameters = listOf(),
                    positionalParameters = listOf(IntType)
                ),
                to = functionType(
                    staticParameters = listOf(typeParameter),
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
                    staticParameters = listOf(),
                    positionalParameters = listOf(TopType)
                ),
                to = functionType(
                    staticParameters = listOf(typeParameter),
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
                to = TupleType(listOf(TopType))
            ),
            equalTo(true)
        )
    }

    @Test
    fun tupleTypeElementsAreNotContravariant() {
        assertThat(
            canCoerce(
                from = TupleType(listOf(TopType)),
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
            from = applyStatic(shapeType, listOf(BoolType)) as Type,
            to = applyStatic(shapeType, listOf(BoolType)) as Type
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
            from = applyStatic(shapeType, listOf(BoolType)) as Type,
            to = applyStatic(shapeType, listOf(IntType)) as Type
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
            from = applyStatic(shapeType, listOf(BoolType)) as Type,
            to = applyStatic(shapeType, listOf(TopType)) as Type
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
            from = applyStatic(shapeType, listOf(TopType)) as Type,
            to = applyStatic(shapeType, listOf(BoolType)) as Type
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
            from = applyStatic(shapeType, listOf(TopType)) as Type,
            to = applyStatic(shapeType, listOf(BoolType)) as Type
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
            from = applyStatic(shapeType, listOf(BoolType)) as Type,
            to = applyStatic(shapeType, listOf(TopType)) as Type
        )
        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun canCoerceShapeToShapeWithSubsetOfFields() {
        val shapeId = freshTypeId()
        val field1 = field(name = "Field1", type = IntType)
        val field2 = field(name = "Field2", type = IntType)
        val field3 = field(name = "Field3", type = IntType)

        val superShapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field1, field2, field3)
        )
        val subShapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field1, field3)
        )
        val canCoerce = canCoerce(from = superShapeType, to = subShapeType)
        assertThat(canCoerce, equalTo(true))
    }

    @Test
    fun cannotCoerceShapeToShapeWithSupersetOfFields() {
        val shapeId = freshTypeId()
        val field1 = field(name = "Field1", type = IntType)
        val field2 = field(name = "Field2", type = IntType)
        val field3 = field(name = "Field3", type = IntType)

        val superShapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field1, field2, field3)
        )
        val subShapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field1, field3)
        )
        val canCoerce = canCoerce(from = subShapeType, to = superShapeType)
        assertThat(canCoerce, equalTo(false))
    }

    @Test
    fun canCoerceBetweenShapeAndUpdatedTypeWithFieldFromShape() {
        val shapeId = freshTypeId()
        val field1 = field(name = "field1", shapeId = shapeId, type = IntType)
        val field2 = field(name = "field2", shapeId = shapeId, type = IntType)
        val field3 = field(name = "field3", shapeId = shapeId, type = IntType)
        val shapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field1, field2, field3)
        )

        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)

        val updatedType = updatedType(
            baseType = typeParameter,
            shapeType = shapeType,
            field = field1,
        )

        assertSymmetricCoercion(
            parameters = setOf(typeParameter),
            from = shapeType,
            to = updatedType,
            matcher = isSuccess(
                typeParameter to isShapeType(
                    shapeId = equalTo(shapeId),
                    populatedFields = isSequence(
                        isField(name = isIdentifier("field2")),
                        isField(name = isIdentifier("field3")),
                    )
                ),
            ),
        )
    }

    @Test
    fun cannotCoerceBetweenShapeAndUpdatedTypeWithFieldFromOtherShape() {
        val shapeId = freshTypeId()
        val field = field(name = "field", shapeId = shapeId, type = IntType)
        val shapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field)
        )

        val otherShapeId = freshTypeId()
        val otherField = field(name = "field", shapeId = otherShapeId, type = IntType)
        val otherShapeType = shapeType(
            shapeId = otherShapeId,
            name = "Box",
            fields = listOf(otherField)
        )

        val typeParameter = invariantTypeParameter("T", shapeId = otherShapeId)

        val updatedType = updatedType(
            baseType = typeParameter,
            shapeType = otherShapeType,
            field = otherField,
        )

        assertSymmetricCoercion(
            parameters = setOf(typeParameter),
            from = shapeType,
            to = updatedType,
            matcher = isFailure,
        )
    }

    @Test
    fun cannotCoerceFromPartialShapeWithFieldUnpopulatedToUpdatedType() {
        val shapeId = freshTypeId()
        val field = field(name = "field", shapeId = shapeId, type = IntType)
        val shapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field)
        )

        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)

        val updatedType = updatedType(
            baseType = typeParameter,
            shapeType = shapeType,
            field = field,
        )

        val result = coerce(
            parameters = setOf(typeParameter),
            from = createEmptyShapeType(shapeType),
            to = updatedType,
        )

        assertThat(result, isFailure)
    }

    @Test
    fun canCoerceFromUpdatedTypeToPartialShapeWithFieldUnpopulated() {
        val shapeId = freshTypeId()
        val field1 = field(name = "field1", shapeId = shapeId, type = IntType)
        val field2 = field(name = "field2", shapeId = shapeId, type = IntType)
        val field3 = field(name = "field3", shapeId = shapeId, type = IntType)
        val shapeType = shapeType(
            shapeId = shapeId,
            name = "Box",
            fields = listOf(field1, field2, field3),
        )

        val typeParameter = invariantTypeParameter("T", shapeId = shapeId)

        val updatedType = updatedType(
            baseType = typeParameter,
            shapeType = shapeType,
            field = field1,
        )

        val result = coerce(
            parameters = setOf(typeParameter),
            from = updatedType,
            to = createPartialShapeType(shapeType, populatedFieldNames = setOf(field2.name)),
        )

        assertThat(result, isSuccess(
            typeParameter to isShapeType(
                shapeId = equalTo(shapeId),
                populatedFields = isSequence(
                    isField(name = isIdentifier("field2")),
                )
            ),
        ))
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
        assertThat(result, isSuccess(typeParameter to isUnionType(members = isSequence(cast(sameInstance(member1)), cast(sameInstance(member2))))))
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

        val result = coerce(
            constraints = listOf(typeParameter2 to IntType, typeParameter1 to shapeType(fields = listOf(field(name = "x", type = typeParameter2)))),
            parameters = setOf(typeParameter1, typeParameter2)
        )

        assertThat(result, isSuccess(
            typeParameter2 to isIntType,
            typeParameter1 to isShapeType(
                populatedFields = isSequence(
                    isField(type = isIntType)
                )
            ),
        ))
    }

    @Test
    fun whenTypeParameterIsBoundThenExistingBoundValueHaveTypesReplaced() {
        val typeParameter1 = invariantTypeParameter("T1")
        val typeParameter2 = invariantTypeParameter("T2")

        val result = coerce(
            constraints = listOf(typeParameter1 to shapeType(fields = listOf(field(name = "x", type = typeParameter2))), typeParameter2 to IntType),
            parameters = setOf(typeParameter1, typeParameter2)
        )

        assertThat(result, isSuccess(
            typeParameter1 to isShapeType(
                populatedFields = isSequence(
                    isField(type = isIntType)
                )
            ),
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
        val solver = TypeConstraintSolver(parameters = setOf())
        assertThat(
            solver.coerceEffect(from = IoEffect, to = effectParameter),
            equalTo(false)
        )
        assertThat(solver.effectBindings[effectParameter], absent())
    }

    @Test
    fun whenEffectParameterIsInParameterSetThenCoercingEffectToEffectParameterBindsEffectParameter() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(parameters = setOf(effectParameter))
        assertThat(
            solver.coerceEffect(from = IoEffect, to = effectParameter),
            equalTo(true)
        )
        assertThat(solver.effectBindings[effectParameter], present(cast(equalTo(IoEffect))))
    }

    @Test
    fun givenEffectParameterIsBoundWhenCoercingEffectToEffectParameterThenEffectParameterIsTreatedAsBoundEffect() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(parameters = setOf(effectParameter))
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
        val solver = TypeConstraintSolver(parameters = setOf())
        assertThat(
            solver.coerceEffect(from = effectParameter, to = IoEffect),
            equalTo(false)
        )
        assertThat(solver.effectBindings[effectParameter], absent())
    }

    @Test
    fun whenEffectParameterIsInParameterSetThenCoercingEffectParameterToEffectBindsEffectParameter() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(parameters = setOf(effectParameter))
        assertThat(
            solver.coerceEffect(from = effectParameter, to = IoEffect),
            equalTo(true)
        )
        assertThat(solver.effectBindings[effectParameter], present(cast(equalTo(IoEffect))))
    }

    @Test
    fun givenEffectParameterIsBoundWhenCoercingEffectParameterToEffectThenEffectParameterIsTreatedAsBoundEffect() {
        val effectParameter = effectParameter("E")
        val solver = TypeConstraintSolver(parameters = setOf(effectParameter))
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

    private fun assertSymmetricCoercion(parameters: Set<StaticParameter>, from: Type, to: Type, matcher: Matcher<CoercionResult>) {
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

    private fun isSuccess(vararg bindings: Pair<TypeParameter, Matcher<Type>>): Matcher<CoercionResult> {
        return cast(has(CoercionResult.Success::bindings, isMap(*bindings)))
    }

    private val isFailure = isA<CoercionResult.Failure>()
}
