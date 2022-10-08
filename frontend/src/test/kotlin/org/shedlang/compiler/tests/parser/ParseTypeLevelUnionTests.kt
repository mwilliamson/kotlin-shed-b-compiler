package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.parser.parseTypeLevelExpression
import org.shedlang.compiler.tests.isSequence

class ParseTypeLevelUnionTests {
    @Test
    fun membersOfTypeLevelUnionAreSeparatedByBars() {
        val source = "A | B"
        val node = parseString(::parseTypeLevelExpression, source)

        assertThat(node, isTypeLevelUnionNode(isSequence(
            isTypeLevelReferenceNode("A"),
            isTypeLevelReferenceNode("B")
        )))
    }
}
