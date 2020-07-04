package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.typeCheckFunctionDeclaration
import org.shedlang.compiler.typechecker.typeCheckFunctionStatement
import org.shedlang.compiler.types.*

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

    @Test
    fun resumeHasNothingType() {
        val node = resume(literalBool())
        val type = typeCheckFunctionStatement(node, typeContext(resumeValueType = BoolType))
        assertThat(type, isNothingType)
    }

    @Test
    fun whenResumeValueIsWrongTypeThenErrorIsThrown() {
        val node = resume(literalBool())

        assertThat(
            { typeCheckFunctionStatement(node, typeContext(resumeValueType = IntType)) },
            throws(allOf(
                has(UnexpectedTypeError::expected, cast(isIntType)),
                has(UnexpectedTypeError::actual, isBoolType)
            ))
        )
    }
}
