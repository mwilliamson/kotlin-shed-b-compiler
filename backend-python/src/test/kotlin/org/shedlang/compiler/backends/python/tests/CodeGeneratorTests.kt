package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.isSequence
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
            isPythonIntegerLiteral(42)
        )))
    }

    @Test
    fun returnStatementGeneratesReturnStatement() {
        val shed = returns(literalInt(42))

        val node = generateCode(shed)

        assertThat(node, cast(has(
            PythonReturnNode::expression,
            isPythonIntegerLiteral(42)
        )))
    }

    @Test
    fun ifStatementGeneratesIfStatement() {
        val shed = ifStatement(
            literalInt(42),
            listOf(returns(literalInt(0))),
            listOf(returns(literalInt(1)))
        )

        val node = generateCode(shed)

        assertThat(node, cast(allOf(
            has(PythonIfStatementNode::condition, isPythonIntegerLiteral(42)),
            has(PythonIfStatementNode::trueBranch, isSequence(
                isPythonReturn(isPythonIntegerLiteral(0))
            )),
            has(PythonIfStatementNode::falseBranch, isSequence(
                isPythonReturn(isPythonIntegerLiteral(1))
            ))
        )))
    }

    @Test
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, isPythonBooleanLiteral())
    }
    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, isPythonIntegerLiteral(42))
    }

    @Test
    fun variableReferenceGenerateVariableReference() {
        val shed = variableReference("x")

        val node = generateCode(shed)

        assertThat(node, isPythonVariableReference("x"))
    }

    private fun isPythonReturn(expression: Matcher<PythonExpressionNode>)
        : Matcher<PythonStatementNode>
        = cast(has(PythonReturnNode::expression, expression))

    private fun isPythonBooleanLiteral()
        : Matcher<PythonExpressionNode>
        = cast(has(PythonBooleanLiteralNode::value, equalTo(true)))

    private fun isPythonIntegerLiteral(value: Int)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonIntegerLiteralNode::value, equalTo(value)))

    private fun isPythonVariableReference(name: String)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonVariableReferenceNode::name, equalTo(name)))
}
