package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseFunctionStatement

class ParseExpressionStatementTests {
    @Test
    fun expressionWithTrailingSemiColonIsReadAsNonReturningExpressionStatement() {
        val source = "4;"
        val parsedStatement = parseString(::parseFunctionStatement, source)
        assertThat(parsedStatement.node, isExpressionStatement(isIntLiteral(4)))
        assertThat(parsedStatement.isReturn, equalTo(false))
    }

    @Test
    fun expressionWithoutTrailingSemiColonIsReadAsReturningExpressionStatement() {
        val source = "4"
        val parsedStatement = parseString(::parseFunctionStatement, source)
        assertThat(parsedStatement.node, isExpressionStatement(isIntLiteral(4)))
        assertThat(parsedStatement.isReturn, equalTo(true))
    }
}
