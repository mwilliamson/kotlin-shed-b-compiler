package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleResult
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.frontend.tests.throwsException
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
            intReference to MetaType(IntType),
            unitReference to MetaType(UnitType)
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
            unitReference to MetaType(UnitType)
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
    fun shapeCallTypeIsShapeType() {
        val shapeReference = variableReference("X")
        val node = call(receiver = shapeReference)

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))
        val type = inferCallType(node, typeContext)

        assertThat(type, cast(equalTo(shapeType)))
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

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))
        val type = inferCallType(node, typeContext)

        assertThat(type, isShapeType(
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
            shapeReference to MetaType(shapeType),
            argumentReference to functionType(positionalParameters = listOf(IntType), returns = UnitType)
        ))
        val type = inferCallType(node, typeContext)

        assertThat(type, isShapeType(
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

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
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
            fields = listOf()
        )
        val node = call(receiver = shapeReference, namedArguments = listOf())

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        val type = inferCallType(node, typeContext)
        assertThat(type, isShapeType(
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

        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        val type = inferCallType(node, typeContext)
        assertThat(type, isShapeType(
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
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferCallType(node, typeContext) },
            throws<PositionalArgumentPassedToShapeConstructorError>()
        )
    }

    @Test
    fun errorWhenShapeCallIsMissingField() {
        val shapeReference = variableReference("X")
        val node = call(receiver = shapeReference)

        val shapeType = shapeType(name = "X", fields = listOf(field("a", BoolType)))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

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
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

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
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

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
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

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
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

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
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
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
                effectReference to EffectType(IoEffect)
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
                effectReference to EffectType(IoEffect)
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
                effectReference to EffectType(IoEffect)
            ),
            effect = EmptyEffect
        )
        assertThat(
            { inferCallType(node, typeContext) },
            throwsException(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun castTypeIsFunctionFromUnionToOptionalMember() {
        val tag = tag(listOf("Example"), "Union")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val union = unionType("Union", tag = tag, members = listOf(member1, member2))

        val optionType = parametrizedShapeType(
            name = "Option",
            parameters = listOf(covariantTypeParameter("T"))
        )
        val optionsModule = ModuleResult.Found(Module.Native(
            name = listOf(Identifier("Core"), Identifier("Options")),
            type = moduleType(fields = mapOf(
                "Option" to MetaType(optionType)
            ))
        ))

        val castReference = variableReference("cast")
        val unionReference = variableReference("Union")
        val memberReference = variableReference("Member1")
        val node = call(
            receiver = castReference,
            positionalArguments = listOf(
                unionReference,
                memberReference
            )
        )

        val typeContext = typeContext(
            modules = mapOf(
                ImportPath.absolute(listOf("Core", "Options")) to optionsModule
            ),
            referenceTypes = mapOf(
                castReference to CastType,
                memberReference to MetaType(member1),
                unionReference to MetaType(union)
            )
        )
        val type = inferCallType(node, typeContext)

        assertThat(type, isEquivalentType(functionType(
            positionalParameters = listOf(union),
            returns = applyStatic(optionType, listOf(member1))
        )))

        assertThat(
            typeContext.toTypes().discriminatorForCast(node),
            isDiscriminator(
                tagValue = equalTo(tagValue(tag, "Member1"))
            )
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
    }
}
