package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.UnaryOperator
import org.shedlang.compiler.parser.parseExpression

class ParseUnaryOperationTests {
    @Test
    fun canParseNotOperation() {
        val source = "not x"
        val node = parseString(::parseExpression, source)
        assertThat(node, isUnaryOperation(
            UnaryOperator.NOT,
            isVariableReference("x")
        ))
    }
}
