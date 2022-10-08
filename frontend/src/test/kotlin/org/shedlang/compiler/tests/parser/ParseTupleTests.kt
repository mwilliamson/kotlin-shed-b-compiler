package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isSequence

class ParseTupleTests {
    @Test
    fun canParseTupleWithNoElements() {
        val source = "#()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isTupleNodeNode(
            elements = isSequence()
        ))
    }

    @Test
    fun canParseSingletonTuple() {
        val source = "#(1)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isTupleNodeNode(
            elements = isSequence(isIntLiteralNode(1))
        ))
    }

    @Test
    fun canParseTuplesWithMultipleElements() {
        val source = "#(1, 2)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isTupleNodeNode(
            elements = isSequence(isIntLiteralNode(1), isIntLiteralNode(2))
        ))
    }

    @Test
    fun elementsCanHaveTrailingComma() {
        val source = "#(1,)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isTupleNodeNode(
            elements = isSequence(isIntLiteralNode(1))
        ))
    }
}
