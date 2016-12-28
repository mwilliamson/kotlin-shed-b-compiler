package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.IntegerLiteralNode
import org.shedlang.compiler.parser.parsePrimaryExpression

class ParsePrimaryExpressionTests {
    @Test
    fun integerTokenCanBeParsedAsIntegerLiteral() {
        val source = "1"
        val node = parseString(::parsePrimaryExpression, source)
        assertThat(node, cast(has(IntegerLiteralNode::value, equalTo(1))))
    }
}