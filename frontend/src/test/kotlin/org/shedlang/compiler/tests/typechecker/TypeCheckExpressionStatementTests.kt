package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.typeCheckFunctionDeclaration
import org.shedlang.compiler.typechecker.typeCheckFunctionStatement
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.StaticValueType
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
        val functionReference = variableReference("f")
        val expressionStatement = expressionStatementTailRecReturn(
            call(receiver = functionReference)
        )
        val boolReference = staticReference("Bool")
        val boolDeclaration = declaration("Bool")
        val functionDeclaration = function(
            name = "f",
            body = listOf(
                expressionStatement
            ),
            returnType = boolReference
        )

        val context = typeContext(
            references = mapOf(
                functionReference to functionDeclaration,
                boolReference to boolDeclaration
            ),
            types = mapOf(
                boolDeclaration to StaticValueType(BoolType)
            )
        )
        typeCheckFunctionDeclaration(functionDeclaration, context)
        context.undefer()
    }

    @Test
    fun exitHasTypeOfExpression() {
        val node = exit(literalBool())
        val type = typeCheckFunctionStatement(node, typeContext())
        assertThat(type, isBoolType)
    }
}
