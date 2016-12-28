package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.IntegerLiteralNode
import org.shedlang.compiler.ast.ReturnNode
import org.shedlang.compiler.parser.tryParseReturn

class ParseReturnTests {
    @Test
    fun expressionIsReadForReturnStatements() {
        val source = "return 4;"
        val node = parseString(::tryParseReturn, source)!!
        assertThat(node, has(ReturnNode::expression, cast(has(IntegerLiteralNode::value, equalTo(4)))))
    }
}