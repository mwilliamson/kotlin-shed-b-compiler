package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.testing.*
import org.shedlang.compiler.typechecker.ReturnCheckError
import org.shedlang.compiler.typechecker.alwaysReturns
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.UnitType

class ReturnCheckTests {
    @Test
    fun checkingReturnsInModuleChecksBodiesOfFunctions() {
        val intType = staticReference("Int")
        val typeContext = typeContext(referenceTypes = mapOf(intType to MetaType(IntType)))
        val node = function(
            returnType = intType,
            body = listOf()
        )


        assertThat(
            {
                typeCheck(node, typeContext)
                typeContext.undefer()
            },
            throws(cast(has(
                ReturnCheckError::message,
                equalTo("function is missing return statement")
            )))
        )
    }

    @Test
    fun functionThatHasUnitReturnTypeDoesntNeedReturnStatement() {
        val unitType = staticReference("Unit")
        val typeContext = typeContext(referenceTypes = mapOf(unitType to MetaType(UnitType)))
        val node = function(
            returnType = unitType,
            body = listOf()
        )
        typeCheck(node, typeContext)
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

    @Test
    fun valStatementNeverReturns() {
        val node = valStatement()
        assertThat(alwaysReturns(node), equalTo(false))
    }
}
