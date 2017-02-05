package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseShape

class ParseShapeTests {
    @Test
    fun expressionIsReadForExpressionStatements() {
        val source = "shape X { }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            name = equalTo("X")
        ))
    }
}
