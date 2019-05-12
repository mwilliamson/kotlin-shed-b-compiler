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
        assertThat(node, isTupleNode(
            elements = isSequence()
        ))
    }
}