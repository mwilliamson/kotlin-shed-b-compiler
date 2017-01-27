package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.typechecker.ReturnCheckError
import org.shedlang.compiler.typechecker.alwaysReturns
import org.shedlang.compiler.typechecker.checkReturns

class ReturnCheckTests {
    @Test
    fun checkingReturnsInModuleChecksBodiesOfFunctions() {
        val node = module(listOf(function(name = "f", body = listOf())))
        assertThat(
            { checkReturns(node) },
            throws(cast(has(ReturnCheckError::message, equalTo("function f is missing return statement"))))
        )
    }

    @Test
    fun returnStatementAlwaysReturns() {
        val node = returns()
        assertThat(alwaysReturns(node), equalTo(true))
    }

    @Test
    fun ifStatementWhenBothBranchesReturnThenStatementReturns() {
        val node = ifStatement(
            condition = variableReference("x"),
            trueBranch = listOf(returns()),
            falseBranch = listOf(returns())
        )
        assertThat(alwaysReturns(node), equalTo(true))
    }

    @Test
    fun ifStatementWhenTrueBranchDoesNotReturnThenStatementDoesNotReturn() {
        val node = ifStatement(
            condition = variableReference("x"),
            trueBranch = listOf(),
            falseBranch = listOf(returns())
        )
        assertThat(alwaysReturns(node), equalTo(false))
    }

    @Test
    fun ifStatementWhenFalseBranchDoesNotReturnThenStatementDoesNotReturn() {
        val node = ifStatement(
            condition = variableReference("x"),
            trueBranch = listOf(returns()),
            falseBranch = listOf()
        )
        assertThat(alwaysReturns(node), equalTo(false))
    }

    @Test
    fun expressionStatementNeverReturns() {
        val node = expressionStatement()
        assertThat(alwaysReturns(node), equalTo(false))
    }
}
