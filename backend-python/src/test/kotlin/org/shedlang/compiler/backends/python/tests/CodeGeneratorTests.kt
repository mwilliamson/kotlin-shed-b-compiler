package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.tests.typechecker.*

class CodeGeneratorTests {
    @Test
    fun emptyModuleGeneratesEmptyModule() {
        val shed = ModuleNode(
            name = "example",
            body = listOf(),
            source = anySourceLocation()
        )

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonModuleNode::statements, equalTo(listOf()))))
    }

    @Test
    fun expressionStatementGeneratesExpressionStatement() {
        val shed = expressionStatement(literalInt(42))

        val node = generateCode(shed)

        assertThat(node, cast(has(
            PythonExpressionStatementNode::expression,
            cast(has(PythonIntegerLiteralNode::value, equalTo(42)))
        )))
    }

    @Test
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonBooleanLiteralNode::value, equalTo(true))))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonIntegerLiteralNode::value, equalTo(42))))
    }

    @Test
    fun variableReferenceGenerateVariableReference() {
        val shed = variableReference("x")

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonVariableReferenceNode::name, equalTo("x"))))
    }
}
