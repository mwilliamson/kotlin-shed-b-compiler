package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckCallTests {
    @Test
    fun functionCallTypeIsReturnTypeOfFunction() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)

        val typeContext = typeContext(referenceTypes = mapOf(functionReference to positionalFunctionType(listOf(), IntType)))
        val type = inferCallType(node, typeContext)

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
                positionalParameters = listOf(),
                namedParameters = mapOf(Identifier("x") to BoolType),
                returns = IntType
            ))
        )

        assertThat(
            { inferCallType(node, typeContext) },
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
            positionalParameters = listOf(argumentTypeParameter),
            returns = returnTypeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType,
            intReference to IntMetaType,
            unitReference to UnitMetaType
        ))
        val type = inferCallType(node, typeContext)

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
            positionalParameters = listOf(),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType,
            unitReference to UnitMetaType
        ))

        assertThat(
            { inferCallType(node, typeContext) },
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
            positionalParameters = listOf(typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        val type = inferCallType(node, typeContext)

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
            positionalParameters = listOf(typeParameter, typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        val type = inferCallType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun typeParameterTakesUnionTypeWhenUsedWithMultipleTypes() {
        val tag = tag(listOf("Example"), "Union")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))

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
            positionalParameters = listOf(typeParameter, typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType,
                member1Reference to member1,
                member2Reference to member2
            )
        )
        val type = inferCallType(node, typeContext)

        assertThat(type, isUnionType(members = isSequence(isEquivalentType(member1), isEquivalentType(member2))))
    }

    @Test
    fun whenTypeParameterIsBoundByEarlierArgumentThenBoundValueIsUsedAsHintForFunctionWithInferredParameterTypes() {
        val functionReference = variableReference("f")
        val parameter = parameter(name = "x", type = null)
        val parameterReference = variableReference("x")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(
                literalInt(),
                functionExpression(
                    parameters = listOf(parameter),
                    body = listOf(
                        expressionStatementReturn(binaryOperation(BinaryOperator.ADD, literalInt(),
                            parameterReference
                        ))
                    ),
                ),
            )
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalParameters = listOf(
                typeParameter,
                functionType(
                    positionalParameters = listOf(typeParameter),
                    returns = typeParameter
                )
            ),
            returns = typeParameter
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            references = mapOf(parameterReference to parameter)
        )
        val type = inferCallType(node, typeContext)
        typeContext.undefer()

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun whenTypeParameterIsBoundByLaterArgumentThenBoundValueIsUsedAsHintForFunctionWithInferredParameterTypes() {
        val functionReference = variableReference("f")
        val parameter = parameter(name = "x", type = null)
        val parameterReference = variableReference("x")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(
                functionExpression(
                    parameters = listOf(parameter),
                    body = listOf(
                        expressionStatementReturn(binaryOperation(BinaryOperator.ADD, literalInt(),
                            parameterReference
                        ))
                    ),
                ),
                literalInt(),
            )
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalParameters = listOf(
                functionType(
                    positionalParameters = listOf(typeParameter),
                    returns = typeParameter
                ),
                typeParameter,
            ),
            returns = typeParameter
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            references = mapOf(parameterReference to parameter)
        )
        val type = inferCallType(node, typeContext)
        typeContext.undefer()

        assertThat(type, cast(equalTo(IntType)))
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
            positionalParameters = listOf(typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            argReference to typeParameter,
            functionReference to functionType
        ))
        val type = inferCallType(node, typeContext)

        assertThat(type, cast(equalTo(typeParameter)))
    }

    @Test
    fun givenAllTypeParametersAreBoundWhenArgumentIsWrongTypeThenExpectedTypeHasTypesReplaced() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(), literalBool())
        )

        val typeParameter = invariantTypeParameter(name = "T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalParameters = listOf(typeParameter, TupleType(listOf(typeParameter))),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        assertThat(
            { inferCallType(node, typeContext) },
            throwsUnexpectedType(expected = cast(isTupleType(isSequence(isIntType))))
        )
    }

    @Test
    fun givenSomeTypeParametersAreNotBoundWhenArgumentIsWrongTypeThenExpectedTypeHasBoundTypesReplaced() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(), literalBool())
        )

        val typeParameter1 = invariantTypeParameter(name = "T1")
        val typeParameter2 = invariantTypeParameter(name = "T2")
        val functionType = functionType(
            staticParameters = listOf(typeParameter1, typeParameter2),
            positionalParameters = listOf(typeParameter1, TupleType(listOf(typeParameter1, typeParameter2))),
            returns = typeParameter1
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        assertThat(
            { inferCallType(node, typeContext) },
            throwsUnexpectedType(expected = cast(isTupleType(isSequence(
                isIntType,
                isTypeParameter(name = isIdentifier("T2")),
            ))))
        )
    }

    @Test
    fun whenFunctionExpressionIsNotFunctionTypeThenCallDoesNotTypeCheck() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(1), literalBool(true))
        )
        assertThat(
            { inferCallType(node, typeContext(referenceTypes = mapOf(functionReference to IntType))) },
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
            { inferCallType(node, typeContext) },
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
            { inferCallType(node, typeContext) },
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
            { inferCallType(node, typeContext) },
            throws(allOf(
                has(WrongNumberOfArgumentsError::expected, equalTo(1)),
                has(WrongNumberOfArgumentsError::actual, equalTo(0))
            ))
        )
    }

    @Test
    fun errorWhenFunctionIsCalledWithSplatArgument() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            namedArguments = listOf(splatArgument(variableReference("x"))),
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(),
        ))
        assertThat(
            { inferCallType(node, typeContext) },
            throwsException<UnexpectedSplatArgumentError>(),
        )
    }

    @Test
    fun shapeCallTypeIsShapeType() {
        val shapeReference = variableReference("X")
        val node = call(receiver = shapeReference)

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to metaType(shapeType)))
        val type = inferCallType(node, typeContext)

        assertThat(type, cast(equalTo(shapeType)))
    }

    @Test
    fun whenSplatArgumentMatchesTypeOfShapeCallThenCallTypeChecks() {
        // TODO: Handling of partial types?
        val shapeReference = variableReference("X")
        val splatArgumentValueReference = variableReference("previous")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(
                splatArgument(splatArgumentValueReference),
                callNamedArgument(name = "first", expression = literalString()),
            ),
        )

        val shapeType = shapeType(
            name = "X",
            fields = listOf(
                field(name = "first", type = StringType),
                field(name = "second", type = IntType),
            ),
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                shapeReference to metaType(shapeType),
                splatArgumentValueReference to shapeType,
            ),
        )
        val type = inferCallType(node, typeContext)

        assertThat(type, cast(equalTo(shapeType)))
    }

    @Test
    fun whenSplatArgumentUsesShapeOfUnrelatedTypeInShapeCallThenCallDoesNotTypeCheck() {
        val shapeReference = variableReference("X")
        val splatArgumentValueReference = variableReference("previous")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(
                splatArgument(splatArgumentValueReference),
                callNamedArgument(name = "first", expression = literalString()),
            ),
        )

        val shapeType = shapeType(
            name = "X",
            fields = listOf(
                field(name = "first", type = StringType),
                field(name = "second", type = IntType),
            ),
        )
        val otherShapeType = shapeType(
            name = "Other",
            fields = listOf(
                field(name = "first", type = StringType),
                field(name = "second", type = IntType),
            ),
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                shapeReference to metaType(shapeType),
                splatArgumentValueReference to otherShapeType,
            ),
        )
        val type = { inferCallType(node, typeContext) }

        assertThat(type, throwsException(allOf(
            has(UnexpectedTypeError::expected, cast(isType(shapeType))),
            has(UnexpectedTypeError::actual, isType(otherShapeType)),
        )))
    }

    @Test
    fun shapeCallWithImplicitTypeArguments() {
        val shapeReference = variableReference("Box")

        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val node = call(receiver = shapeReference, namedArguments = listOf(
            callNamedArgument("value", literalBool())
        ))

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to StaticValueType(shapeType)))
        val type = inferCallType(node, typeContext)

        assertThat(type, isCompleteShapeType(
            name = isIdentifier("Box"),
            staticArguments = isSequence(isBoolType),
            fields = isSequence(isField(name = isIdentifier("value"), type = isBoolType))
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
            fields = listOf(
                field("accept", functionType(positionalParameters = listOf(typeParameter), returns = UnitType))
            )
        )
        val node = call(receiver = shapeReference, namedArguments = listOf(
            callNamedArgument("accept", argumentReference)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            shapeReference to StaticValueType(shapeType),
            argumentReference to functionType(positionalParameters = listOf(IntType), returns = UnitType)
        ))
        val type = inferCallType(node, typeContext)

        assertThat(type, isCompleteShapeType(
            name = isIdentifier("Sink"),
            staticArguments = isSequence(isIntType)
        ))
    }

    @Test
    fun whenInvariantTypeParameterIsNotConstrainedThenErrorIsThrown() {
        val shapeReference = variableReference("Thing")

        val typeParameter = invariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Thing",
            parameters = listOf(typeParameter),
            fields = listOf()
        )
        val node = call(receiver = shapeReference, namedArguments = listOf())

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to StaticValueType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
            throwsException<CouldNotInferTypeParameterError>()
        )
    }

    @Test
    fun whenCovariantTypeParameterIsNotConstrainedThenTypeParameterIsNothing() {
        val shapeReference = variableReference("Thing")

        val typeParameter = covariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Thing",
            parameters = listOf(typeParameter),
            fields = listOf()
        )
        val node = call(receiver = shapeReference, namedArguments = listOf())

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to StaticValueType(shapeType)))

        val type = inferCallType(node, typeContext)
        assertThat(type, isCompleteShapeType(
            staticArguments = isSequence(isNothingType)
        ))
    }

    @Test
    fun whenContravariantTypeParameterIsNotConstrainedThenTypeParameterIsAny() {
        val shapeReference = variableReference("Thing")

        val typeParameter = contravariantTypeParameter("T")
        val shapeType = parametrizedShapeType(
            "Thing",
            parameters = listOf(typeParameter),
            fields = listOf()
        )
        val node = call(receiver = shapeReference, namedArguments = listOf())

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to StaticValueType(shapeType)))

        val type = inferCallType(node, typeContext)
        assertThat(type, isCompleteShapeType(
            staticArguments = isSequence(isAnyType)
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

        val type = inferCallType(node, typeContext)
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
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to metaType(shapeType)))

        val typeCheck = { inferCallType(node, typeContext) }

        assertThat(typeCheck, throwsException(allOf(
            has(WrongNumberOfArgumentsError::actual, equalTo(1)),
            has(WrongNumberOfArgumentsError::expected, equalTo(0)),
        )))
    }

    @Test
    fun errorWhenShapeCallIsMissingField() {
        val shapeReference = variableReference("X")
        val node = call(receiver = shapeReference)

        val shapeType = shapeType(name = "X", fields = listOf(field("a", BoolType)))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to metaType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
            throws(has(MissingArgumentError::argumentName, isIdentifier("a")))
        )
    }

    @Test
    fun errorWhenShapeCallIsPassedWrongTypeForField() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(callNamedArgument("a", literalInt()))
        )

        val shapeType = shapeType(name = "X", fields = listOf(field("a", BoolType)))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to metaType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
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
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to metaType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
            throws(has(ExtraArgumentError::argumentName, isIdentifier("a")))
        )
    }

    @Test
    fun errorWhenShapeCallHasValueForConstantField() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(callNamedArgument("a", literalInt()))
        )

        val shapeType = shapeType(name = "X", fields = listOf(
            field("a", IntType, isConstant = true)
        ))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to metaType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
            throws(has(ExtraArgumentError::argumentName, isIdentifier("a")))
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

        val shapeType = shapeType(name = "X", fields = listOf(field("a", IntType)))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to metaType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
            throws(has(ArgumentAlreadyPassedError::argumentName, isIdentifier("a")))
        )
    }

    @Test
    fun whenEffectIsInScopeThenCanCallFunctionWithEffect() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference, hasEffect = true)
        val functionType = functionType(
            effect = IoEffect,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = IoEffect
        )
        inferCallType(node, typeContext)
    }

    @Test
    fun whenEffectIsInScopeThenCanCallFunctionWithNoEffects() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference, hasEffect = false)
        val functionType = functionType(
            effect = EmptyEffect,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = IoEffect
        )
        inferCallType(node, typeContext)
    }

    @Test
    fun errorWhenCallingFunctionWithEffectNotInScope() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference, hasEffect = true)
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
            { inferCallType(node, typeContext) },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun errorWhenCallingFunctionWithEffectWithoutEffectFlag() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference, hasEffect = false)
        val functionType = functionType(
            effect = IoEffect,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = IoEffect
        )
        assertThat(
            { inferCallType(node, typeContext) },
            throwsException<CallWithEffectMissingEffectFlag>()
        )
    }

    @Test
    fun errorWhenCallingFunctionWithoutEffectWithEffectFlag() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference, hasEffect = true)
        val functionType = functionType(
            effect = EmptyEffect,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effect = EmptyEffect
        )
        assertThat(
            { inferCallType(node, typeContext) },
            throws(isA<ReceiverHasNoEffectsError>())
        )
    }

    @Test
    fun canCallFunctionWithExplicitEffectArgument() {
        val effectParameter = effectParameter("E")
        val effectReference = staticReference("Io")

        val functionReference = variableReference("f")

        val node = call(
            receiver = functionReference,
            staticArguments = listOf(effectReference),
            hasEffect = true
        )
        val functionType = functionType(
            staticParameters = listOf(effectParameter),
            effect = effectParameter,
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType,
                effectReference to effectType(IoEffect)
            ),
            effect = EmptyEffect
        )
        assertThat(
            { inferCallType(node, typeContext) },
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
            positionalArguments = listOf(otherFunctionReference),
            hasEffect = true
        )
        val functionType = functionType(
            staticParameters = listOf(effectParameter),
            positionalParameters = listOf(functionType(
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
                effectReference to effectType(IoEffect)
            ),
            effect = EmptyEffect
        )
        assertThat(
            { inferCallType(node, typeContext) },
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
            positionalArguments = listOf(otherFunctionReference),
            hasEffect = true
        )
        val functionType = functionType(
            staticParameters = listOf(effectParameter),
            positionalParameters = listOf(functionType(
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
                effectReference to effectType(IoEffect)
            ),
            effect = EmptyEffect
        )
        assertThat(
            { inferCallType(node, typeContext) },
            throwsException(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Nested
    inner class VarargsTests {
        private val headTypeParameter = invariantTypeParameter("Head")
        private val tailTypeParameter = invariantTypeParameter("Tail")

        private val consType = functionType(
            staticParameters = listOf(headTypeParameter, tailTypeParameter),
            positionalParameters = listOf(headTypeParameter, tailTypeParameter),
            returns = TupleType(elementTypes = listOf(headTypeParameter, tailTypeParameter))
        )

        private val varargsType = VarargsType(
            name = Identifier("list"),
            cons = consType,
            nil = UnitType
        )

        private val receiverReference = variableReference("list")

        @Test
        fun varargsCallOfZeroArgumentsHasTypeOfNil() {
            val node = call(receiver = receiverReference)

            val typeContext = typeContext(
                referenceTypes = mapOf(receiverReference to varargsType)
            )
            val type = inferCallType(node, typeContext)

            assertThat(type, isUnitType)
        }

        @Test
        fun varargsCallOfOneArgumentAppliesConsOnce() {
            val argumentReference = variableReference("one")

            val node = call(
                receiver = receiverReference,
                positionalArguments = listOf(argumentReference)
            )

            val typeContext = typeContext(
                referenceTypes = mapOf(
                    receiverReference to varargsType,
                    argumentReference to IntType
                )
            )
            val type = inferCallType(node, typeContext)

            assertThat(type, isTupleType(elementTypes = isSequence(
                isIntType,
                isUnitType
            )))
        }

        @Test
        fun varargsCallOfTwoArgumentsAppliesConsTwice() {
            val argumentReference1 = variableReference("one")
            val argumentReference2 = variableReference("two")

            val node = call(
                receiver = receiverReference,
                positionalArguments = listOf(argumentReference1, argumentReference2)
            )

            val typeContext = typeContext(
                referenceTypes = mapOf(
                    receiverReference to varargsType,
                    argumentReference1 to IntType,
                    argumentReference2 to StringType
                )
            )
            val type = inferCallType(node, typeContext)

            assertThat(type, isTupleType(elementTypes = isSequence(
                isIntType,
                isTupleType(elementTypes = isSequence(
                    isStringType,
                    isUnitType
                ))
            )))
        }

        @Test
        fun errorWhenVarargsFunctionIsCalledWithSplatArgument() {
            val node = call(
                receiver = receiverReference,
                namedArguments = listOf(splatArgument(variableReference("x"))),
            )

            val typeContext = typeContext(
                referenceTypes = mapOf(
                    receiverReference to varargsType,
                )
            )

            assertThat(
                { inferCallType(node, typeContext) },
                throwsException<UnexpectedSplatArgumentError>(),
            )
        }
    }

    @Test
    fun whenReceiverIsPartialCallThenImplicitTypeArgumentsAreFoundByConsideringCombinedCall() {
        val tag = tag(moduleName = listOf("ModuleName"), name = "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))

        val typeParameter = invariantTypeParameter("T")
        val functionType = functionType(
            staticParameters = listOf(typeParameter),
            positionalParameters = listOf(typeParameter, typeParameter),
            returns = typeParameter,
        )

        val functionReference = variableReference("f")
        val member1Reference = variableReference("member1")
        val member2Reference = variableReference("member2")

        val node = call(
            receiver = partialCall(
                receiver = functionReference,
                staticArguments = listOf(),
                positionalArguments = listOf(member1Reference),
            ),
            staticArguments = listOf(),
            positionalArguments = listOf(member2Reference),
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType,
                member1Reference to member1,
                member2Reference to member2,
            )
        )
        val type = inferType(node, typeContext)

        assertThat(type, isUnionType(members = isSequence(isEquivalentType(member1), isEquivalentType(member2))))
    }
}
