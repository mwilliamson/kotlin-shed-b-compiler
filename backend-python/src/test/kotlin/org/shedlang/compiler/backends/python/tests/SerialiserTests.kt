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
        // TODO: bracketing/precedence
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
}
