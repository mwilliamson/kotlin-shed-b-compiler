package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.MissingHandlerError
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.types.effectType
import org.shedlang.compiler.types.functionType

class TypeCheckHandleTests {
    @Test
    fun typeOfHandleExpressionIsTypeOfUnionOfBodyAndHandlers() {
        val tag = tag(listOf("Example"), "X")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val effectReference = staticReference("Try")
        val functionReference = variableReference("f")
        val member2Reference = variableReference("member2")
        val effect = computationalEffect(
            name = Identifier("Try"),
            operations = mapOf(
                Identifier("throw") to functionType()
            )
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
    fun whenHandlerForOperationIsMissingThenErrorIsThrown() {
        val effectReference = staticReference("Try")
        val effect = computationalEffect(
            name = Identifier("Try"),
            operations = mapOf(
                Identifier("throw") to functionType()
            )
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
}
