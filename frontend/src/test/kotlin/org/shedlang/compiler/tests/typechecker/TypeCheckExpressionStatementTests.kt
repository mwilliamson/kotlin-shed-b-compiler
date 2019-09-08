package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.typeCheckFunctionStatement
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.UnitType

class TypeCheckExpressionStatementTests {
    @Test
    fun expressionIsTypeChecked() {
        val functionReference = variableReference("f")
        val node = expressionStatement(call(functionReference))
        assertThat(
            { typeCheckFunctionStatement(node, typeContext(referenceTypes = mapOf(functionReference to UnitType))) },
            throws(has(UnexpectedTypeError::actual, equalTo<Type>(UnitType)))
        )
    }

    @Test
    fun nonReturningExpressionStatementHasUnitType() {
        val node = expressionStatementNoReturn(literalBool())
        val type = typeCheckFunctionStatement(node, typeContext())
        assertThat(type, isUnitType)
    }

    @Test
    fun returningExpressionStatementHasTypeOfExpression() {
        val node = expressionStatementReturn(literalBool())
        val type = typeCheckFunctionStatement(node, typeContext())
        assertThat(type, isBoolType)
    }

    @Test
    fun tailrecExpressionStatementHasTypeOfExpression() {
        val node = expressionStatementTailRecReturn(literalBool())
        val type = typeCheckFunctionStatement(node, typeContext())
        assertThat(type, isBoolType)
    }
}
