package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.python.ast.PythonOperator
import org.shedlang.compiler.backends.python.serialise

class SerialiserTests {
    @Test
    fun expressionStatementSerialisation() {
        assertThat(
            serialise(pythonExpressionStatement(pythonLiteralBoolean(true))),
            equalTo("True\n")
        )
    }

    @Test
    fun returnSerialisation() {
        assertThat(
            serialise(pythonReturn(pythonLiteralBoolean(true))),
            equalTo("return True\n")
        )
    }

    @Test
    fun serialisingIfStatementWithBothBranches() {
        assertThat(
            serialise(pythonIf(
                pythonLiteralBoolean(true),
                listOf(pythonReturn(pythonLiteralInt(0))),
                listOf(pythonReturn(pythonLiteralInt(1)))
            )),
            equalTo(listOf(
                "if True:",
                "    return 0",
                "else:",
                "    return 1",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun booleanSerialisation() {
        assertThat(
            serialise(pythonLiteralBoolean(true)),
            equalTo("True")
        )
        assertThat(
            serialise(pythonLiteralBoolean(false)),
            equalTo("False")
        )
    }

    @Test
    fun integerSerialisation() {
        val node = pythonLiteralInt(42)
        val output = serialise(node)
        assertThat(output, equalTo("42"))
    }

    @Test
    fun variableReferenceSerialisation() {
        val node = pythonVariableReference("x")
        val output = serialise(node)
        assertThat(output, equalTo("x"))
    }

    @TestFactory
    fun binaryOperationSerialisation(): List<DynamicTest> {
        return listOf(
            Pair(PythonOperator.EQUALS, "x == y"),
            Pair(PythonOperator.ADD, "x + y"),
            Pair(PythonOperator.SUBTRACT, "x - y"),
            Pair(PythonOperator.MULTIPLY, "x * y")
        ).map({ operator -> DynamicTest.dynamicTest(operator.second, {
            val node = pythonBinaryOperation(
                operator = operator.first,
                left = pythonVariableReference("x"),
                right = pythonVariableReference("y")
            )
            val output = serialise(node)
            assertThat(output, equalTo(operator.second))
        })})
    }

    @Test
    fun leftSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
        val node = pythonBinaryOperation(
            operator = PythonOperator.MULTIPLY,
            left = pythonBinaryOperation(
                PythonOperator.ADD,
                pythonVariableReference("x"),
                pythonVariableReference("y")
            ),
            right = pythonVariableReference("z")
        )
        val output = serialise(node)
        assertThat(output, equalTo("(x + y) * z"))
    }

    @Test
    fun rightSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
        val node = pythonBinaryOperation(
            operator = PythonOperator.MULTIPLY,
            left = pythonVariableReference("x"),
            right = pythonBinaryOperation(
                PythonOperator.ADD,
                pythonVariableReference("y"),
                pythonVariableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x * (y + z)"))
    }

    @Test
    fun leftSubExpressionIsNotBracketedForLeftAssociativeOperators() {
        val node = pythonBinaryOperation(
            operator = PythonOperator.ADD,
            left = pythonBinaryOperation(
                PythonOperator.ADD,
                pythonVariableReference("x"),
                pythonVariableReference("y")
            ),
            right = pythonVariableReference("z")
        )
        val output = serialise(node)
        assertThat(output, equalTo("x + y + z"))
    }

    @Test
    fun rightSubExpressionIsBracketedForLeftAssociativeOperators() {
        val node = pythonBinaryOperation(
            operator = PythonOperator.ADD,
            left = pythonVariableReference("x"),
            right = pythonBinaryOperation(
                PythonOperator.ADD,
                pythonVariableReference("y"),
                pythonVariableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x + (y + z)"))
    }

    @Test
    fun functionCallSerialisation() {
        val node = pythonFunctionCall(
            function = pythonVariableReference("f"),
            arguments = listOf(
                pythonVariableReference("x"),
                pythonVariableReference("y")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("f(x, y)"))
    }

    @Test
    fun functionInFunctionCallIsNotBracketedWhenOfSamePrecedence() {
        val node = pythonFunctionCall(
            function = pythonFunctionCall(
                pythonVariableReference("f"),
                arguments = listOf()
            ),
            arguments = listOf()
        )
        val output = serialise(node)
        assertThat(output, equalTo("f()()"))
    }

    @Test
    fun functionInFunctionCallIsBracketedWhenOfLowerPrecedence() {
        val node = pythonFunctionCall(
            function = pythonBinaryOperation(
                PythonOperator.ADD,
                pythonVariableReference("f"),
                pythonVariableReference("g")
            ),
            arguments = listOf()
        )
        val output = serialise(node)
        assertThat(output, equalTo("(f + g)()"))
    }
}
