package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.VariableReferenceNode
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolutionContext
import org.shedlang.compiler.typechecker.UnresolvedReferenceError
import org.shedlang.compiler.typechecker.resolve

class ResolutionTests {
    @Test
    fun variableReferencesAreResolved() {
        val node = variableReference("x")
        val context = resolutionContext(mapOf("x" to 42))

        resolve(node, context)

        assertThat(context[node], equalTo(42))
    }

    @Test
    fun exceptionWhenVariableNotInScope() {
        val node = variableReference("x")
        val context = resolutionContext(mapOf())

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, equalTo("x")))
        )
    }

    @Test
    fun typeReferencesAreResolved() {
        val node = typeReference("X")
        val context = resolutionContext(mapOf("X" to 42))

        resolve(node, context)

        assertThat(context[node], equalTo(42))
    }

    @Test
    fun exceptionWhenTypeVariableNotInScope() {
        val node = typeReference("X")
        val context = resolutionContext(mapOf())

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, equalTo("X")))
        )
    }

    @Test
    fun childrenAreResolved() {
        val node = VariableReferenceNode("x", anySource())
        val context = resolutionContext(mapOf("x" to 42))

        resolve(expressionStatement(node), context)

        assertThat(context[node], equalTo(42))
    }

    @Test
    fun functionArgumentsAreAddedToScope() {
        val reference = variableReference("x")
        val argument = argument(name = "x", type = typeReference("Int"))
        val node = function(
            arguments = listOf(argument),
            returnType = typeReference("Int"),
            body = listOf(returns(reference))
        )

        val context = resolutionContext(mapOf("Int" to -1))
        resolve(node, context)

        assertThat(context[reference], equalTo(argument.nodeId))
    }

    @Test
    fun functionArgumentsCanShadowExistingVariables() {
        val reference = variableReference("x")
        val argument = argument(name = "x", type = typeReference("Int"))
        val node = function(
            arguments = listOf(argument),
            returnType = typeReference("Int"),
            body = listOf(returns(reference))
        )

        val context = resolutionContext(mapOf(reference.name to argument.nodeId + 1000, "Int" to -1))
        resolve(node, context)

        assertThat(context[reference], equalTo(argument.nodeId))
    }

    @Test
    fun valIntroducesVariableToFunctionScope() {
        val reference = variableReference("x")
        val valStatement = valStatement(name = "x", expression = literalInt())
        val node = function(
            arguments = listOf(),
            returnType = typeReference("Int"),
            body = listOf(
                valStatement,
                returns(reference)
            )
        )

        val context = resolutionContext(mapOf("Int" to -1))
        resolve(node, context)

        assertThat(context[reference], equalTo(valStatement.nodeId))
    }

    @Test
    fun functionsCanCallEachOtherRecursively() {
        val referenceToSecond = variableReference("g")
        val definitionOfFirst = function(name = "f", body = listOf(
            expressionStatement(functionCall(referenceToSecond, listOf()))
        ))
        val referenceToFirst = variableReference("f")
        val definitionOfSecond = function(name = "g", body = listOf(
            expressionStatement(functionCall(referenceToFirst, listOf()))
        ))
        val node = module(body = listOf(
            definitionOfFirst,
            definitionOfSecond
        ))

        val context = resolutionContext(mapOf("Unit" to -1))
        resolve(node, context)

        assertThat(context[referenceToFirst], equalTo(definitionOfFirst.nodeId))
        assertThat(context[referenceToSecond], equalTo(definitionOfSecond.nodeId))
    }

    private fun resolutionContext(variables: Map<String, Int>)
        = ResolutionContext(bindings = variables, nodes = mutableMapOf())
}
