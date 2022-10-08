package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.frontend.parser.InconsistentBranchTerminationError
import org.shedlang.compiler.frontend.parser.parseFunctionStatement

class ParseExpressionStatementTests {
    @Test
    fun expressionWithTrailingSemiColonIsReadAsNonReturningExpressionStatement() {
        val source = "4;"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isExpressionStatementNode(
            isIntLiteralNode(4),
            type = equalTo(ExpressionStatementNode.Type.NO_VALUE)
        ))
    }

    @Test
    fun expressionWithoutTrailingSemiColonIsReadAsReturningExpressionStatement() {
        val source = "4"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isExpressionStatementNode(
            isIntLiteralNode(4),
            type = equalTo(ExpressionStatementNode.Type.VALUE)
        ))
    }

    @Test
    fun expressionWithTailrecPrefixIsReadAsTailrecReturningExpressionStatement() {
        val source = "tailrec f()"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isExpressionStatementNode(
            isCallNode(),
            type = equalTo(ExpressionStatementNode.Type.TAILREC)
        ))
    }

    @Test
    fun whenAllBranchesOfIfReturnThenIfExpressionIsReadAsReturning() {
        val source = "if (true) { 1 } else { 2 }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node.terminatesBlock, equalTo(true))
    }

    @Test
    fun whenAllBranchesOfIfDoNotReturnThenIfExpressionIsReadAsNotReturning() {
        val source = "if (true) { 1; } else { 2; }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node.terminatesBlock, equalTo(false))
    }

    @Test
    fun whenSomeBranchesOfIfReturnThenErrorIsThrown() {
        val source = "if (true) { 1 } else { 2; }"
        assertThat({
            parseString(::parseFunctionStatement, source)
        }, throwsException<InconsistentBranchTerminationError>())
    }

    @Test
    fun whenAllBranchesOfWhenReturnThenWhenExpressionIsReadAsReturning() {
        val source = "when (x) { is T1 { 1 } is T2 { 2 } }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node.terminatesBlock, equalTo(true))
    }

    @Test
    fun whenAllBranchesOfWhenDoNotReturnThenWhenExpressionIsReadAsNotReturning() {
        val source = "when (x) { is T1 { 1; } is T2 { 2; } }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node.terminatesBlock, equalTo(false))
    }

    @Test
    fun whenSomeBranchesOfWhenReturnThenErrorIsThrown() {
        val source = "when (x) { is T1 { 1 } is T2 { 2; } }"
        assertThat({
            parseString(::parseFunctionStatement, source)
        }, throwsException<InconsistentBranchTerminationError>())
    }
}
