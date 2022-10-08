package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.parser.parseExpression
import org.shedlang.compiler.tests.isIdentifier

class ParseFieldAccessTests {
    @Test
    fun canParseFieldAccess() {
        val source = "x.y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isFieldAccessNode(
            receiver = isVariableReferenceNode("x"),
            fieldName = isIdentifier("y"),
            source = isStringSource("x.y", 1)
        ))
    }

    @Test
    fun fieldAccessIsLeftAssociative() {
        val source = "x.y.z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isFieldAccessNode(
            receiver = isFieldAccessNode(
                receiver = isVariableReferenceNode("x"),
                fieldName = isIdentifier("y")
            ),
            fieldName = isIdentifier("z")
        ))
    }
}
