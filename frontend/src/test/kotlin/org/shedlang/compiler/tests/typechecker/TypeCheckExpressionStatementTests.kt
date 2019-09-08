package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.InvalidTailCall
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
                boolDeclaration to MetaType(BoolType)
            )
        )
        typeCheckFunctionDeclaration(functionDeclaration, context)
        context.undefer()
    }

    @Test
    fun whenTailrecExpressionIsNotFunctionCallThenErrorIsThrown() {
        val expressionStatement = expressionStatementTailRecReturn(literalBool())
        val boolReference = staticReference("Bool")
        val boolDeclaration = declaration("Bool")
        val functionDeclaration = function(
            name = "f",
            body = listOf(expressionStatement),
            returnType = boolReference
        )

        val context = typeContext(
            references = mapOf(
                boolReference to boolDeclaration
            ),
            types = mapOf(
                boolDeclaration to MetaType(BoolType)
            )
        )

        assertThat(
            {
                typeCheckFunctionDeclaration(functionDeclaration, context)
                context.undefer()
            },
            throws<InvalidTailCall>()
        )
    }

    @Test
    fun whenTailrecExpressionIsFunctionCallToOtherFunctionThenErrorIsThrown() {
        val otherFunctionReference = variableReference("other")
        val expressionStatement = expressionStatementTailRecReturn(
            call(receiver = otherFunctionReference)
        )
        val boolReference = staticReference("Bool")
        val boolDeclaration = declaration("Bool")
        val functionDeclaration = function(
            name = "f",
            body = listOf(expressionStatement),
            returnType = boolReference
        )
        val otherFunctionDeclaration = function(
            name = "other"
        )

        val context = typeContext(
            references = mapOf(
                boolReference to boolDeclaration,
                otherFunctionReference to otherFunctionDeclaration
            ),
            types = mapOf(
                boolDeclaration to MetaType(BoolType),
                otherFunctionDeclaration to functionType(returns = BoolType)
            )
        )

        assertThat(
            {
                typeCheckFunctionDeclaration(functionDeclaration, context)
                context.undefer()
            },
            throws<InvalidTailCall>()
        )
    }
}
