package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseStaticExpression
import org.shedlang.compiler.tests.isSequence

class ParseStaticUnionTests {
    @Test
    fun membersOfStaticUnionAreSeparatedByBars() {
        val source = "A | B"
        val node = parseString(::parseStaticExpression, source)

        assertThat(node, isStaticUnion(isSequence(
            isStaticReference("A"),
            isStaticReference("B")
        )))
    }
}
