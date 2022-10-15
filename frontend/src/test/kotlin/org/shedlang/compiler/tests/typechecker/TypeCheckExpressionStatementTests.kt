package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckExpressionStatementTests {
    @Test
    fun expressionIsTypeChecked() {
        val functionReference = variableReference("f")
        val node = expressionStatement(call(functionReference))
        assertThat(
            { typeCheckFunctionStatementAllPhases(node, typeContext(referenceTypes = mapOf(functionReference to UnitType))) },
            throws(has(UnexpectedTypeError::actual, equalTo<Type>(UnitType)))
        )
    }

    @Test
    fun nonReturningExpressionStatementHasUnitType() {
        val node = expressionStatementNoReturn(literalBool())
        val type = typeCheckFunctionStatementAllPhases(node, typeContext())
        assertThat(type, isUnitType)
    }

    @Test
    fun returningExpressionStatementHasTypeOfExpression() {
        val node = expressionStatementReturn(literalBool())
        val type = typeCheckFunctionStatementAllPhases(node, typeContext())
        assertThat(type, isBoolType)
    }

    @Test
    fun tailrecExpressionStatementHasTypeOfExpression() {
        val functionReference = variableReference("f")
        val expressionStatement = expressionStatementTailRecReturn(
            call(receiver = functionReference)
        )
        val boolReference = typeLevelReference("Bool")
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
                boolDeclaration to BoolMetaType
            )
        )
        typeCheckModuleStatementAllPhases(functionDeclaration, context)
    }

    @Test
    fun exitHasTypeOfExpression() {
        val node = exit(literalBool())
        val type = typeCheckFunctionStatementAllPhases(node, typeContext())
        assertThat(type, isBoolType)
    }

    @Test
    fun resumeHasNothingType() {
        val node = resume(literalBool())

        val context = typeContext(handle = HandleTypes(resumeValueType = BoolType, stateType = null))
        val type = typeCheckFunctionStatementAllPhases(node, context)

        assertThat(type, isNothingType)
    }

    @Test
    fun whenResumeValueIsWrongTypeThenErrorIsThrown() {
        val node = resume(literalBool())

        val context = typeContext(handle = HandleTypes(resumeValueType = IntType, stateType = null))
        assertThat(
            { typeCheckFunctionStatementAllPhases(node, context) },
            throws(allOf(
                has(UnexpectedTypeError::expected, cast(isIntType)),
                has(UnexpectedTypeError::actual, isBoolType)
            ))
        )
    }

    @Test
    fun resumeOutsideOfHandlerThrowsError() {
        val node = resume()

        val context = typeContext(handle = null)
        assertThat(
            { typeCheckFunctionStatementAllPhases(node, context) },
            throwsException<CannotResumeOutsideOfHandler>(),
        )
    }

    @Test
    fun whenExpressionHasNothingTypeThenExpressionStatementHasNothingType() {
        val receiver = variableReference("f")
        val node = expressionStatementNoReturn(call(receiver))
        val context = typeContext(
            referenceTypes = mapOf(receiver to functionType(returns = NothingType)),
        )

        val type = typeCheckFunctionStatementAllPhases(node, context)

        assertThat(type, isNothingType)
    }
}
