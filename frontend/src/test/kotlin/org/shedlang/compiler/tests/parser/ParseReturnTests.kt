package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.IntegerLiteralNode
import org.shedlang.compiler.parser.parseFunctionStatement

class ParseReturnTests {
    @Test
    fun expressionIsReadForReturnStatements() {
        val source = "return 4;"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isReturn(has(IntegerLiteralNode::value, equalTo(4))))
    }
}
