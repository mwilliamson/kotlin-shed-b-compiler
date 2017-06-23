package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

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
        assertStatementInStatementIsTypeChecked { badStatement -> ifStatement(trueBranch = listOf(badStatement)) }
    }

    @Test
    fun falseBranchIsTypeChecked() {
        assertStatementInStatementIsTypeChecked { badStatement -> ifStatement(falseBranch = listOf(badStatement)) }
    }

    // TODO: Test that refined type is only in true branch (not false branch, nor following statements)
    @Test
    fun whenConditionIsIsOperationThenTypeIsRefinedInTrueBranch() {
        val argument = argument("x")

        val variableReference = variableReference("x")
        val intType = typeReference("Int")
        val statement = ifStatement(
            condition = isOperation(variableReference, intType),
            trueBranch = listOf(
                returns(variableReference)
            )
        )

        val typeContext = typeContext(
            returnType = IntType,
            referenceTypes = mapOf(
                intType to MetaType(IntType)
            ),
            references = mapOf(
                variableReference to argument
            ),
            types = mapOf(
                argument to object: UnionType {
                    override val name = "X"
                    override val members = listOf(IntType, UnitType)
                }
            )
        )

        typeCheck(statement, typeContext)
    }
}
