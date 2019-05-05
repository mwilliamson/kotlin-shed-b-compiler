package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseStaticExpression
import org.shedlang.compiler.tests.isSequence

class ParseTupleTypeTests {
    @Test
    fun emptyTupleType() {
        val source = "#()"
        val node = parseString(::parseStaticExpression, source)

        assertThat(node, isTupleTypeNode(isSequence()))
    }
}