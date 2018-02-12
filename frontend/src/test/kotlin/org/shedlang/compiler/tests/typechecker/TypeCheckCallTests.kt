package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckCallTests {
    @Test
    fun functionCallTypeIsReturnTypeOfFunction() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)

        val typeContext = typeContext(referenceTypes = mapOf(functionReference to positionalFunctionType(listOf(), IntType)))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun typeOfNamedArgumentsAreCheckedForFunctionCall() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(),
            namedArguments = listOf(callNamedArgument("x", literalUnit()))
        )

        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                positionalArguments = listOf(),
                namedArguments = mapOf("x" to BoolType),
                returns = IntType
            ))
        )

        assertThat(
            { inferType(node, typeContext) },
            throwsUnexpectedType(expected = BoolType, actual = UnitType)
        )
    }

    @Test
    fun functionCallWithExplicitTypeArguments() {
        val functionReference = variableReference("f")
        val intReference = staticReference("Int")
        val unitReference = staticReference("Unit")
        val node = call(
            receiver = functionReference,
            staticArguments = listOf(intReference, unitReference),
            positionalArguments = listOf(literalInt())
        )

        val argumentTypeParameter = invariantTypeParameter(name = "T")
        val returnTypeParameter = invariantTypeParameter(name = "R")
        val functionType = functionType(
            staticParameters = listOf(argumentTypeParameter, returnTypeParameter),
            positionalArguments = listOf(argumentTypeParameter),
            returns = returnTypeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType,
            intReference to MetaType(IntType),
            unitReference to MetaType(UnitType)
        ))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(UnitType)))
    }

    @Test
    fun whenWrongNumberOfTypeArgumentsIsProvidedThenErrorIsThrown() {
        val functionReference = variableReference("f")
        val unitReference = staticReference("Unit")
        val node = call(
            receiver = functionReference,
            staticArguments = listOf(unitReference, unitReference),
            positionalArguments = listOf()
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalArguments = listOf(),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType,
            unitReference to MetaType(UnitType)
        ))

        assertThat(
            { inferType(node, typeContext) },
            throws(allOf(
                has(WrongNumberOfStaticArgumentsError::expected, equalTo(1)),
                has(WrongNumberOfStaticArgumentsError::actual, equalTo(2))
            ))
        )
    }

    @Test
    fun functionCallWithImplicitTypeArguments() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt())
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalArguments = listOf(typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun functionCallWithImplicitTypeArgumentsWithTypeParameterInstantiatedToSameTypeTwice() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(), literalInt())
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalArguments = listOf(typeParameter, typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun typeParameterTakesUnionTypeWhenUsedWithMultipleTypes() {
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))

        val functionReference = variableReference("f")
        val member1Reference = variableReference("member1")
        val member2Reference = variableReference("member2")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(member1Reference, member2Reference)
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalArguments = listOf(typeParameter, typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType,
                member1Reference to member1,
                member2Reference to member2
            )
        )
        val type = inferType(node, typeContext)

        assertThat(type, isUnionType(members = isSequence(isType(member1), isType(member2))))
    }

    @Test
    fun canRecursivelyCallFunctionWithInferredTypeParameters() {
        val functionReference = variableReference("f")
        val argReference = variableReference("x")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(argReference)
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalArguments = listOf(typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            argReference to typeParameter,
            functionReference to functionType
        ))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(typeParameter)))
    }

    @Test
    fun whenFunctionExpressionIsNotFunctionTypeThenCallDoesNotTypeCheck() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(1), literalBool(true))
        )
        assertThat(
            { inferType(node, typeContext(referenceTypes = mapOf(functionReference to IntType))) },
            throwsUnexpectedType(expected = positionalFunctionType(listOf(IntType, BoolType), AnyType), actual = IntType)
        )
    }

    @Test
    fun errorWhenArgumentTypesDoNotMatch() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(1))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to positionalFunctionType(listOf(BoolType), IntType)
        ))
        assertThat(
            { inferType(node, typeContext) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun errorWhenExtraArgumentIsPassed() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(1))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to positionalFunctionType(listOf(), IntType)
        ))
        assertThat(
            { inferType(node, typeContext) },
            throws(allOf(
                has(WrongNumberOfArgumentsError::expected, equalTo(0)),
                has(WrongNumberOfArgumentsError::actual, equalTo(1))
            ))
        )
    }

    @Test
    fun errorWhenArgumentIsMissing() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf()
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to positionalFunctionType(listOf(IntType), IntType)
        ))
        assertThat(
            { inferType(node, typeContext) },
            throws(allOf(
                has(WrongNumberOfArgumentsError::expected, equalTo(1)),
                has(WrongNumberOfArgumentsError::actual, equalTo(0))
            ))
        )
    }

    @Test
    fun shapeCallTypeIsShapeType() {
        val shapeReference = variableReference("X")
        val node = call(receiver = shapeReference)

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(shapeType)))
    }

    @Test
    fun shapeCallWithImplicitTypeArguments() {
        val shapeReference = variableReference("Box")

        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = mapOf(
                "value" to typeParameter
            )
        )
        val node = call(receiver = shapeReference, namedArguments = listOf(
            callNamedArgument("value", literalBool())
        ))

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))
        val type = inferType(node, typeContext)

        assertThat(type, isShapeType(
            name = equalTo("Box"),
            typeArguments = isSequence(isBoolType),
            fields = listOf("value" to isBoolType)
        ))
    }

    @Test
    fun shapeCallIgnoresVarianceWhenInferringTypeParameters() {
        val shapeReference = variableReference("Sink")
        val argumentReference = variableReference("write")

        val typeParameter = contravariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Sink",
            parameters = listOf(typeParameter),
            fields = mapOf(
                "accept" to functionType(positionalArguments = listOf(typeParameter), returns = UnitType)
            )
        )
        val node = call(receiver = shapeReference, namedArguments = listOf(
            callNamedArgument("accept", argumentReference)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            shapeReference to MetaType(shapeType),
            argumentReference to functionType(positionalArguments = listOf(IntType), returns = UnitType)
        ))
        val type = inferType(node, typeContext)

        assertThat(type, isShapeType(
            name = equalTo("Sink"),
            typeArguments = isSequence(isIntType)
        ))
    }

    @Test
    fun whenInvariantTypeParameterIsNotConstrainedThenErrorIsThrown() {
        val shapeReference = variableReference("Thing")

        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Thing",
            parameters = listOf(typeParameter),
            fields = mapOf()
        )
        val node = call(receiver = shapeReference, namedArguments = listOf())

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws<CouldNotInferTypeParameterError>()
        )
    }

    @Test
    fun whenCovariantTypeParameterIsNotConstrainedThenTypeParameterIsNothing() {
        val shapeReference = variableReference("Thing")

        val typeParameter = covariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Thing",
            parameters = listOf(typeParameter),
            fields = mapOf()
        )
        val node = call(receiver = shapeReference, namedArguments = listOf())

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        val type = inferType(node, typeContext)
        assertThat(type, isShapeType(
            typeArguments = isSequence(isNothingType)
        ))
    }

    @Test
    fun whenContravariantTypeParameterIsNotConstrainedThenTypeParameterIsAny() {
        val shapeReference = variableReference("Thing")

        val typeParameter = contravariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Thing",
            parameters = listOf(typeParameter),
            fields = mapOf()
        )
        val node = call(receiver = shapeReference, namedArguments = listOf())

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        val type = inferType(node, typeContext)
        assertThat(type, isShapeType(
            typeArguments = isSequence(isAnyType)
        ))
    }

    @Test
    fun whenEffectParameterIsNotConstrainedThenEffectParameterIsEmptyEffect() {
        val functionReference = variableReference("f")

        val effectParameter = effectParameter("!E")
        val functionType = functionType(
            staticParameters = listOf(effectParameter),
            effect = effectParameter,
            returns = UnitType
        )
        val node = call(receiver = functionReference)

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = EmptyEffect
        )

        val type = inferType(node, typeContext)
        assertThat(type, isUnitType)
    }

    @Test
    fun errorWhenShapeCallIsPassedPositionalArgument() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
            positionalArguments = listOf(literalBool())
        )

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws<PositionalArgumentPassedToShapeConstructorError>()
        )
    }

    @Test
    fun errorWhenShapeCallIsMissingField() {
        val shapeReference = variableReference("X")
        val node = call(receiver = shapeReference)

        val shapeType = shapeType(name = "X", fields = mapOf("a" to BoolType))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(MissingArgumentError::argumentName, equalTo("a")))
        )
    }

    @Test
    fun errorWhenShapeCallIsPassedWrongTypeForField() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(callNamedArgument("a", literalInt()))
        )

        val shapeType = shapeType(name = "X", fields = mapOf("a" to BoolType))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun errorWhenShapeCallHasExtraField() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(callNamedArgument("a", literalInt()))
        )

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(ExtraArgumentError::argumentName, equalTo("a")))
        )
    }

    @Test
    fun errorWhenSameNamedArgumentIsPassedMultipleTimes() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(
                callNamedArgument("a", literalInt()),
                callNamedArgument("a", literalInt())
            )
        )

        val shapeType = shapeType(name = "X", fields = mapOf("a" to IntType))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(ArgumentAlreadyPassedError::argumentName, equalTo("a")))
        )
    }

    @Test
    fun whenEffectIsInScopeThenCanCallFunctionWithEffect() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)
        val functionType = functionType(
            effect = IoEffect,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = IoEffect
        )
        inferType(node, typeContext)
    }

    @Test
    fun whenEffectIsInScopeThenCanCallFunctionWithNoEffects() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)
        val functionType = functionType(
            effect = EmptyEffect,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = IoEffect
        )
        inferType(node, typeContext)
    }

    @Test
    fun errorWhenCallingFunctionWithEffectNotInScope() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)
        val functionType = functionType(
            effect = IoEffect,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = object : Effect {
                override val shortDescription: String
                    get() = "async"
            }
        )
        assertThat(
            { inferType(node, typeContext) },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun canCallFunctionWithExplicitEffectArgument() {
        val effectParameter = effectParameter("E")
        val effectReference = staticReference("Io")

        val functionReference = variableReference("f")

        val node = call(
            receiver = functionReference,
            staticArguments = listOf(effectReference)
        )
        val functionType = functionType(
            staticParameters = listOf(effectParameter),
            effect = effectParameter,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType,
                effectReference to EffectType(IoEffect)
            ),
            effect = EmptyEffect
        )
        assertThat(
            { inferType(node, typeContext) },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun explicitEffectArgumentsReplaceEffectParameterInParameters() {
        val effectParameter = effectParameter("E")
        val effectReference = staticReference("Io")

        val functionReference = variableReference("f")
        val otherFunctionReference = variableReference("g")

        val node = call(
            receiver = functionReference,
            staticArguments = listOf(effectReference),
            positionalArguments = listOf(otherFunctionReference)
        )
        val functionType = functionType(
            staticParameters = listOf(effectParameter),
            positionalArguments = listOf(functionType(
                effect = effectParameter,
                returns = UnitType
            )),
            effect = effectParameter,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType,
                otherFunctionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                effectReference to EffectType(IoEffect)
            ),
            effect = EmptyEffect
        )
        assertThat(
            { inferType(node, typeContext) },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun canCallFunctionWithImplicitEffectArgument() {
        val effectParameter = effectParameter("E")
        val effectReference = staticReference("Io")

        val functionReference = variableReference("f")
        val otherFunctionReference = variableReference("g")

        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(otherFunctionReference)
        )
        val functionType = functionType(
            staticParameters = listOf(effectParameter),
            positionalArguments = listOf(functionType(
                effect = effectParameter,
                returns = UnitType
            )),
            effect = effectParameter,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType,
                otherFunctionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                effectReference to EffectType(IoEffect)
            ),
            effect = EmptyEffect
        )
        assertThat(
            { inferType(node, typeContext) },
            throwsException(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun listCallOfZeroElementsReturnsListOfNothing() {
        val listReference = variableReference("list")
        val node = call(receiver = listReference)

        val typeContext = typeContext(referenceTypes = mapOf(listReference to ListConstructorType))
        val type = inferType(node, typeContext)

        assertThat(type, isListType(isNothingType))
    }

    @Test
    fun listCallOfSingleElementReturnsListOfElementType() {
        val listReference = variableReference("list")
        val node = call(
            receiver = listReference,
            positionalArguments = listOf(literalInt())
        )

        val typeContext = typeContext(referenceTypes = mapOf(listReference to ListConstructorType))
        val type = inferType(node, typeContext)

        assertThat(type, isListType(isIntType))
    }

    @Test
    fun listCallOfElementsOfSameTypeReturnsListOfElementType() {
        val listReference = variableReference("list")
        val node = call(
            receiver = listReference,
            positionalArguments = listOf(literalInt(), literalInt())
        )

        val typeContext = typeContext(referenceTypes = mapOf(listReference to ListConstructorType))
        val type = inferType(node, typeContext)

        assertThat(type, isListType(isIntType))
    }

    @Test
    fun listCallOfElementsOfDifferentTypeReturnsListOfUnionOfElementTypes() {
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))

        val member1Reference = variableReference("member1")
        val member2Reference = variableReference("member2")

        val listReference = variableReference("list")
        val node = call(
            receiver = listReference,
            positionalArguments = listOf(member1Reference, member2Reference)
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                listReference to ListConstructorType,
                member1Reference to member1,
                member2Reference to member2
            )
        )
        val type = inferType(node, typeContext)

        assertThat(type, isListType(isUnionType(members = isSequence(isType(member1), isType(member2)))))
    }
}
