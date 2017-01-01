package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.typechecker.BoolType
import org.shedlang.compiler.typechecker.IntType
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckIfStatementTests {
    @Test
    fun whenConditionIsNotBooleanThenIfStatementDoesNotTypeCheck() {
        val statement = ifStatement(condition = literalInt(1))
        assertThat(
            { typeCheck(statement, emptyTypeContext()) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun trueBranchIsTypeChecked() {
        assertStatementIsTypeChecked { badStatement -> ifStatement(trueBranch = listOf(badStatement)) }
    }

    @Test
    fun falseBranchIsTypeChecked() {
        assertStatementIsTypeChecked { badStatement -> ifStatement(falseBranch = listOf(badStatement)) }
    }
}
