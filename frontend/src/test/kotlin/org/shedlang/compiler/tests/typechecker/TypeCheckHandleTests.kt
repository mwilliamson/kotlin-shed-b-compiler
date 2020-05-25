package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckHandleTests {
    @Test
    fun typeOfHandleExpressionIsTypeOfUnionOfBodyAndHandlers() {
        val tag = tag(listOf("Example"), "X")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val effectReference = staticReference("Try")
        val functionReference = variableReference("f")
        val member2Reference = variableReference("member2")
        val effect = createEffect(
            name = Identifier("Try"),
            getOperations = { effect ->
                mapOf(
                    Identifier("throw") to functionType(effect = effect)
                )
            }
        )

        val expression = handle(
            effect = effectReference,
            body = block(listOf(
                expressionStatementReturn(call(functionReference, hasEffect = true))
            )),
            handlers = listOf(
                Identifier("throw") to functionExpression(
                    body = listOf(
                        expressionStatementReturn(member2Reference)
                    ),
                    inferReturnType = true
                )
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
    fun whenEffectIsNotComputationalEffectThenErrorIsThrown() {
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
        assertThat({ inferType(expression, context) }, throws<ExpectedComputationalEffectError>())
    }

    @Test
    fun whenHandlerHasWrongTypeThenErrorIsThrown() {
        val booleanReference = staticReference("Bool")
        val effectReference = staticReference("Try")
        val effect = createEffect(
            name = Identifier("Try"),
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
                Identifier("throw") to functionExpression(
                    parameters = listOf(parameter(type = booleanReference)),
                    body = listOf(),
                    inferReturnType = true
                )
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
    fun whenOperationTypeHasWrongEffectThenErrorIsThrown() {
        val effectReference = staticReference("Try")
        val effect = createEffect(
            name = Identifier("Try"),
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
                Identifier("throw") to functionExpression(
                    body = listOf(),
                    inferReturnType = true
                )
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
        val effect = createEffect(
            name = Identifier("Try"),
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
        val effect = createEffect(
            name = Identifier("Try"),
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
                Identifier("throw") to functionExpression(
                    body = listOf(),
                    inferReturnType = true
                ),
                Identifier("raise") to functionExpression(
                    body = listOf(),
                    inferReturnType = true
                )
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

    private fun createEffect(
        name: Identifier,
        getOperations: (ComputationalEffect) -> Map<Identifier, FunctionType>
    ): ComputationalEffect {
        var effect: ComputationalEffect? = null
        effect = computationalEffect(
            name = name,
            getOperations = lazy {
                getOperations(effect!!)
            }
        )
        return effect
    }
}
