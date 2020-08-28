package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isSequence

class ParsePipelineTests {
    @Test
    fun canParsePipelineOperator() {
        val source = "x |> y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            receiver = isVariableReference("y"),
            positionalArguments = isSequence(
                isVariableReference("x")
            ),
            namedArguments = isSequence(),
            typeArguments = isSequence()
        ))
    }

    @Test
    fun pipelineOperatorIsLeftAssociative() {
        val source = "x |> y |> z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            receiver = isVariableReference("z"),
            positionalArguments = isSequence(
                isCall(
                    receiver = isVariableReference("y"),
                    positionalArguments = isSequence(
                        isVariableReference("x")
                    )
                )
            )
        ))
    }
}
