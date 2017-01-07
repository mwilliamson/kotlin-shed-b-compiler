package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.TypeReferenceNode
import org.shedlang.compiler.ast.VariableReferenceNode
import org.shedlang.compiler.typechecker.ResolutionContext
import org.shedlang.compiler.typechecker.UnresolvedReferenceError
import org.shedlang.compiler.typechecker.resolve

class ResolutionTests {
    @Test
    fun variableReferencesAreResolved() {
        val node = VariableReferenceNode("x", anySourceLocation())
        val context = resolutionContext(mapOf("x" to 42))

        resolve(node, context)

        assertThat(context[node], equalTo(42))
    }

    @Test
    fun exceptionWhenVariableNotInScope() {
        val node = VariableReferenceNode("x", anySourceLocation())
        val context = resolutionContext(mapOf())

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, equalTo("x")))
        )
    }

    @Test
    fun typeReferencesAreResolved() {
        val node = TypeReferenceNode("X", anySourceLocation())
        val context = resolutionContext(mapOf("X" to 42))

        resolve(node, context)

        assertThat(context[node], equalTo(42))
    }

    @Test
    fun exceptionWhenTypeVariableNotInScope() {
        val node = TypeReferenceNode("X", anySourceLocation())
        val context = resolutionContext(mapOf())

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, equalTo("X")))
        )
    }

    @Test
    fun childrenAreResolved() {
        val node = VariableReferenceNode("x", anySourceLocation())
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

    private fun resolutionContext(variables: Map<String, Int>)
        = ResolutionContext(bindings = variables, nodes = mutableMapOf())
}
