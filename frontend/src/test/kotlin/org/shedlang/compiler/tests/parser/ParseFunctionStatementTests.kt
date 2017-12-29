package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.parser.parseFunctionStatement
import org.shedlang.compiler.tests.allOf

class ParseFunctionStatementTests {
    @Test
    fun whenFunctionBodyIsNotValidStatementThenExceptionIsThrown() {
        val source = "module"
        assertThat(
            { parseString(::parseFunctionStatement, source) },
            throws(allOf(
                has(UnexpectedTokenException::expected, equalTo("function statement")),
                has(UnexpectedTokenException::actual, equalTo("KEYWORD_MODULE: module"))
            ))
        )
    }

    @Test
    fun ifStatementReturnsWhenAllBranchesReturns() {
        val source = "if (true) { 1 } else { 2 }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isExpressionStatement(isReturn = equalTo(true)))
    }

    @Test
    fun ifStatementDoesNotReturnWhenAllBranchesDoNotReturn() {
        val source = "if (true) { 1; } else { 2; }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isExpressionStatement(isReturn = equalTo(false)))
    }
}
