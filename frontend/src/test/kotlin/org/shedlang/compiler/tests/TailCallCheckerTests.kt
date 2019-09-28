package org.shedlang.compiler.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.ReferenceNode
import org.shedlang.compiler.ast.VariableBindingNode
import org.shedlang.compiler.frontend.checkTailCalls
import org.shedlang.compiler.typechecker.InvalidTailCall
import org.shedlang.compiler.typechecker.ResolvedReferencesMap

class TailCallCheckerTests {
    @Test
    fun tailrecExpressionCanBeCallToSameFunction() {
        val functionReference = variableReference("f")
        val expressionStatement = expressionStatementTailRecReturn(
            call(receiver = functionReference)
        )
        val functionDeclaration = function(
            name = "f",
            body = listOf(
                expressionStatement
            )
        )

        val references = createReferences(
            functionReference to functionDeclaration
        )
        checkTailCalls(functionDeclaration, references = references)
    }

    @Test
    fun whenTailrecExpressionIsNotFunctionCallThenErrorIsThrown() {
        val expressionStatement = expressionStatementTailRecReturn(literalBool())
        val functionDeclaration = function(
            name = "f",
            body = listOf(expressionStatement)
        )

        assertThat(
            {
                checkTailCalls(functionDeclaration, references = createReferences())
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
        val functionDeclaration = function(
            name = "f",
            body = listOf(expressionStatement)
        )
        val otherFunctionDeclaration = function(
            name = "other"
        )

        val references = createReferences(
            otherFunctionReference to otherFunctionDeclaration
        )

        assertThat(
            {
                checkTailCalls(functionDeclaration, references = references)
            },
            throws<InvalidTailCall>()
        )
    }

    @Test
    fun whenValidTailrecExpressionIsInReturningIfExpressionThenCheckPasses() {
        val functionReference = variableReference("f")
        val expressionStatement = expressionStatementReturn(
            ifExpression(
                literalBool(),
                listOf(expressionStatementTailRecReturn(call(receiver = functionReference))),
                listOf()
            )
        )
        val functionDeclaration = function(
            name = "f",
            body = listOf(
                expressionStatement
            )
        )

        val references = createReferences(
            functionReference to functionDeclaration
        )
        checkTailCalls(functionDeclaration, references = references)
    }

    @Test
    fun whenInvalidTailrecExpressionIsInReturningIfExpressionThenCheckFails() {
        val expressionStatement = expressionStatementReturn(
            ifExpression(
                literalBool(),
                listOf(expressionStatementTailRecReturn(literalBool())),
                listOf()
            )
        )
        val functionDeclaration = function(
            name = "f",
            body = listOf(expressionStatement)
        )

        assertThat(
            {
                checkTailCalls(functionDeclaration, references = createReferences())
            },
            throws<InvalidTailCall>()
        )
    }

    @Test
    fun whenValidTailrecExpressionIsInReturningWhenExpressionThenCheckPasses() {
        val functionReference = variableReference("f")
        val expressionStatement = expressionStatementReturn(
            whenExpression(
                expression = literalBool(),
                branches = listOf(),
                elseBranch = listOf(expressionStatementTailRecReturn(call(receiver = functionReference)))
            )
        )
        val functionDeclaration = function(
            name = "f",
            body = listOf(
                expressionStatement
            )
        )

        val references = createReferences(
            functionReference to functionDeclaration
        )
        checkTailCalls(functionDeclaration, references = references)
    }

    @Test
    fun whenInvalidTailrecExpressionIsInReturningWhenExpressionThenCheckFails() {
        val expressionStatement = expressionStatementReturn(
            whenExpression(
                expression = literalBool(),
                branches = listOf(),
                elseBranch = listOf(expressionStatementTailRecReturn(literalBool()))
            )
        )
        val functionDeclaration = function(
            name = "f",
            body = listOf(expressionStatement)
        )

        assertThat(
            {
                checkTailCalls(functionDeclaration, references = createReferences())
            },
            throws<InvalidTailCall>()
        )
    }

    private fun createReferences(vararg references: Pair<ReferenceNode, VariableBindingNode>): ResolvedReferences {
        return ResolvedReferencesMap(references.associate { (reference, binding) ->
            reference.nodeId to binding
        })
    }
}
