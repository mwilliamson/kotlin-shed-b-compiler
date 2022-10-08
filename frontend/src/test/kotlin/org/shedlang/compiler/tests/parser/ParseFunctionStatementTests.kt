package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.parser.UnexpectedTokenError
import org.shedlang.compiler.parser.parseFunctionStatement

class ParseFunctionStatementTests {
    @Test
    fun whenFunctionBodyIsNotValidStatementThenExceptionIsThrown() {
        val source = "module"
        assertThat(
            { parseString(::parseFunctionStatement, source) },
            throws(allOf(
                has(UnexpectedTokenError::expected, equalTo("function statement")),
                has(UnexpectedTokenError::actual, equalTo("KEYWORD_MODULE: module"))
            ))
        )
    }

    @Test
    fun ifStatementReturnsWhenAllBranchesReturns() {
        val source = "if (true) { 1 } else { 2 }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isExpressionStatementNode(type = equalTo(ExpressionStatementNode.Type.VALUE)))
    }

    @Test
    fun ifStatementDoesNotReturnWhenAllBranchesDoNotReturn() {
        val source = "if (true) { 1; } else { 2; }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isExpressionStatementNode(type = equalTo(ExpressionStatementNode.Type.NO_VALUE)))
    }
}
