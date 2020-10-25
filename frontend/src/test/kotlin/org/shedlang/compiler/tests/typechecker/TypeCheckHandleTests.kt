package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckHandleTests {
    @Test
    fun typeOfHandleExpressionIsTypeOfUnionOfBodyAndHandlersThatExit() {
        val tag = tag(listOf("Example"), "X")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val effectReference = staticReference("Try")
        val functionReference = variableReference("f")
        val member2Reference = variableReference("member2")
        val effect = userDefinedEffect(name = Identifier("Try"),
            getOperations = { effect ->
                mapOf(
                    Identifier("throw") to functionType(
                        effect = effect,
                        returns = NothingType
                    ),
                    Identifier("get") to functionType(
                        effect = effect,
                        returns = StringType
                    )
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf(
                expressionStatementReturn(call(functionReference, hasEffect = true))
            )),
            handlers = listOf(
                handler("throw", functionExpression(
                    body = listOf(
                        exit(member2Reference)
                    ),
                    inferReturnType = true
                )),
                handler("get", functionExpression(
                    body = listOf(
                        resume(literalString())
                    ),
                    inferReturnType = true
                ))
            )
        )

        val context = typeContext(
            referenceTypes = mapOf(
                effectReference to effectType(effect),
                functionReference to functionType(returns = member1, effect = effect),
                member2Reference to member2
            )
        )
        val type = inferType(expression, context)

        assertThat(type, isUnionType(members = isSequence(
            isEquivalentType(member1),
            isEquivalentType(member2)
        )))
    }

    @Test
    fun whenHandleContextAllowsEffectThenHandlersCanUseEffect() {
        val effectReference = staticReference("Try")
        val functionReference = variableReference("f")
        val effect = userDefinedEffect(name = Identifier("Try"),
            getOperations = { effect ->
                mapOf(
                    Identifier("throw") to functionType(effect = effect)
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf()),
            handlers = listOf(
                handler("throw", functionExpression(
                    body = listOf(
                        expressionStatementNoReturn(call(functionReference, hasEffect = true)),
                        exit(literalUnit())
                    ),
                    inferReturnType = true
                ))
            )
        )

        val context = typeContext(
            effect = IoEffect,
            referenceTypes = mapOf(
                effectReference to effectType(effect),
                functionReference to functionType(effect = IoEffect)
            )
        )
        inferType(expression, context)
    }

    @Test
    fun whenEffectIsNotUserDefinedEffectThenErrorIsThrown() {
        val effectReference = staticReference("Io")

        val expression = handle(
            effect = effectReference,
            body = block(listOf()),
            handlers = listOf()
        )

        val context = typeContext(
            referenceTypes = mapOf(
                effectReference to effectType(IoEffect)
            )
        )
        assertThat({ inferType(expression, context) }, throws<ExpectedUserDefinedEffectError>())
    }

    @Test
    fun whenHandlerThatExitsHasWrongTypeThenErrorIsThrown() {
        val booleanReference = staticReference("Bool")
        val effectReference = staticReference("Try")
        val effect = userDefinedEffect(name = Identifier("Try"),
            getOperations = { effect ->
                mapOf(
                    Identifier("throw") to functionType(
                        positionalParameters = listOf(StringType),
                        effect = effect,
                        returns = IntType
                    )
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf()),
            handlers = listOf(
                handler("throw", functionExpression(
                    parameters = listOf(parameter(type = booleanReference)),
                    body = listOf(
                        exit(literalUnit())
                    ),
                    inferReturnType = true
                ))
            )
        )

        val context = typeContext(
            referenceTypes = mapOf(
                booleanReference to metaType(BoolType),
                effectReference to effectType(effect)
            )
        )
        assertThat({ inferType(expression, context) }, throws<UnexpectedTypeError>(allOf(
            has(UnexpectedTypeError::expected, cast(isFunctionType(
                positionalParameters = isSequence(isStringType),
                effect = equalTo(EmptyEffect),
                returnType = isAnyType
            )))
        )))
    }

    @Test
    fun whenHandlerThatResumesHasWrongTypeThenErrorIsThrown() {
        val booleanReference = staticReference("Bool")
        val effectReference = staticReference("Get")
        val effect = userDefinedEffect(
            name = Identifier("Get"),
            getOperations = { effect ->
                mapOf(
                    Identifier("get") to functionType(
                        positionalParameters = listOf(StringType),
                        effect = effect,
                        returns = IntType
                    )
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf()),
            handlers = listOf(
                handler("get", functionExpression(
                    parameters = listOf(parameter(type = booleanReference)),
                    body = listOf(
                        resume(literalUnit())
                    ),
                    inferReturnType = true
                ))
            )
        )

        val context = typeContext(
            referenceTypes = mapOf(
                booleanReference to metaType(BoolType),
                effectReference to effectType(effect)
            )
        )
        assertThat({ inferType(expression, context) }, throws<UnexpectedTypeError>(allOf(
            has(UnexpectedTypeError::expected, cast(isIntType)),
            has(UnexpectedTypeError::actual, isUnitType)
        )))
    }

    @Test
    fun whenOperationTypeHasWrongEffectThenErrorIsThrown() {
        val effectReference = staticReference("Try")
        val effect = userDefinedEffect(name = Identifier("Try"),
            getOperations = { _ ->
                mapOf(
                    Identifier("throw") to functionType(
                        effect = IoEffect,
                        returns = IntType
                    )
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf()),
            handlers = listOf(
                handler("throw", functionExpression(
                    body = listOf(
                        exit(literalUnit())
                    ),
                    inferReturnType = true
                ))
            )
        )

        val context = typeContext(
            referenceTypes = mapOf(
                effectReference to effectType(effect)
            )
        )
        assertThat({ inferType(expression, context) }, throws<CompilerError>(allOf(
            has(CompilerError::message, equalTo("operation has unexpected effect"))
        )))
    }

    @Test
    fun whenHandlerForOperationIsMissingThenErrorIsThrown() {
        val effectReference = staticReference("Try")
        val effect = userDefinedEffect(name = Identifier("Try"),
            getOperations = { effect ->
                mapOf(
                    Identifier("throw") to functionType(effect = effect)
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf()),
            handlers = listOf()
        )

        val context = typeContext(
            referenceTypes = mapOf(
                effectReference to effectType(effect)
            )
        )
        assertThat({ inferType(expression, context) }, throws<MissingHandlerError>(
            has(MissingHandlerError::name, isIdentifier("throw"))
        ))
    }

    @Test
    fun whenHandlerForUnknownOperationIsPresentThenErrorIsThrown() {
        val effectReference = staticReference("Try")
        val effect = userDefinedEffect(name = Identifier("Try"),
            getOperations = { effect ->
                mapOf(
                    Identifier("throw") to functionType(effect = effect)
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf()),
            handlers = listOf(
                handler("throw", functionExpression(
                    body = listOf(
                        exit(literalUnit())
                    ),
                    inferReturnType = true
                )),
                handler("raise", functionExpression(
                    body = listOf(
                        exit(literalUnit())
                    ),
                    inferReturnType = true
                ))
            )
        )

        val context = typeContext(
            referenceTypes = mapOf(
                effectReference to effectType(effect)
            )
        )
        assertThat({ inferType(expression, context) }, throws<UnknownOperationError>(allOf(
            has(UnknownOperationError::operationName, isIdentifier("raise")),
            has(UnknownOperationError::effect, cast(equalTo(effect)))
        )))
    }

    @Nested
    inner class WithStateTests {
        private val effect = userDefinedEffect(
            name = Identifier("Get"),
            getOperations = { effect ->
                mapOf(
                    Identifier("get") to functionType(
                        positionalParameters = listOf(StringType),
                        effect = effect,
                        returns = IntType
                    )
                )
            }
        )
        private val effectReference = staticReference("Get")
        private val boolReference = staticReference("Bool")
        private val stringReference = staticReference("String")

        @Test
        fun whenHandleHasNoStateThenResumeWithNewStateThrowsError() {
            val stringReference = staticReference("String")

            val expression = handle(
                effect = effectReference,
                initialState = null,
                body = block(listOf()),
                handlers = listOf(
                    handler("get", functionExpression(
                        parameters = listOf(parameter(type = stringReference)),
                        body = listOf(
                            resume(expression = literalInt(), newState = literalUnit())
                        ),
                        inferReturnType = true
                    ))
                )
            )

            val context = typeContext(
                referenceTypes = mapOf(
                    stringReference to metaType(StringType),
                    effectReference to effectType(effect)
                )
            )
            assertThat({ inferType(expression, context) }, throwsException<CannotResumeWithStateInStatelessHandleError>())
        }

        @Test
        fun whenHandleHasStateThenResumeWithoutNewStateThrowsError() {
            val expression = handle(
                effect = effectReference,
                initialState = literalBool(),
                body = block(listOf()),
                handlers = listOf(
                    handler("get", functionExpression(
                        parameters = listOf(parameter(type = boolReference), parameter(type = stringReference)),
                        body = listOf(
                            resume(expression = literalInt(), newState = null)
                        ),
                        inferReturnType = true
                    ))
                )
            )

            val context = typeContext(
                referenceTypes = mapOf(
                    boolReference to metaType(BoolType),
                    stringReference to metaType(StringType),
                    effectReference to effectType(effect)
                )
            )
            assertThat({ inferType(expression, context) }, throwsException<ResumeMissingNewStateError>())
        }

        @Test
        fun whenHandleHasStateThenResumeWithWrongNewStateTypeThrowsError() {
            val expression = handle(
                effect = effectReference,
                initialState = literalBool(),
                body = block(listOf()),
                handlers = listOf(
                    handler("get", functionExpression(
                        parameters = listOf(parameter(type = boolReference), parameter(type = stringReference)),
                        body = listOf(
                            resume(expression = literalInt(), newState = literalInt())
                        ),
                        inferReturnType = true
                    ))
                )
            )

            val context = typeContext(
                referenceTypes = mapOf(
                    boolReference to metaType(BoolType),
                    stringReference to metaType(StringType),
                    effectReference to effectType(effect)
                )
            )
            assertThat({ inferType(expression, context) }, throwsException(allOf(
                has(UnexpectedTypeError::expected, cast(isBoolType)),
                has(UnexpectedTypeError::actual, isIntType),
            )))
        }

        @Test
        fun handlerForStatefulHandleTakesStateAsFirstArgument() {
            val expression = handle(
                effect = effectReference,
                initialState = literalBool(),
                body = block(listOf()),
                handlers = listOf(
                    handler("get", functionExpression(
                        parameters = listOf(parameter(type = boolReference), parameter(type = stringReference)),
                        body = listOf(
                            resume(expression = literalInt(), newState = literalBool())
                        ),
                        inferReturnType = true
                    ))
                )
            )

            val context = typeContext(
                referenceTypes = mapOf(
                    boolReference to metaType(BoolType),
                    stringReference to metaType(StringType),
                    effectReference to effectType(effect)
                )
            )
            val type = inferType(expression, context)

            assertThat(type, isUnitType)
        }
    }
}
