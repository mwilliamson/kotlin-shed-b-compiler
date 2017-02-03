package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.javascript.serialise
import org.shedlang.compiler.tests.typechecker.literalBool
import org.shedlang.compiler.tests.typechecker.literalInt
import org.shedlang.compiler.tests.typechecker.literalString
import org.shedlang.compiler.tests.typechecker.variableReference

class SerialiserTests {
//    @Test
//    fun moduleSerialisation() {
//        assertThat(
//            serialise(module(listOf(
//                expressionStatement(literalBool(true)),
//                expressionStatement(literalBool(false))
//            ))),
//            equalTo(listOf(
//                "True",
//                "False",
//                ""
//            ).joinToString("\n"))
//        )
//    }
//
//    @Test
//    fun emptyFunctionSerialisation() {
//        assertThat(
//            indentedSerialise(function(name = "f")),
//            equalTo(listOf(
//                "    def f():",
//                "        pass",
//                ""
//            ).joinToString("\n"))
//        )
//    }
//
//    @Test
//    fun functionBodyIsSerialised() {
//        assertThat(
//            indentedSerialise(function(name = "f", body = listOf(
//                returns(literalInt(42))
//            ))),
//            equalTo(listOf(
//                "    def f():",
//                "        return 42",
//                ""
//            ).joinToString("\n"))
//        )
//    }
//
//    @Test
//    fun formalFunctionArgumentsAreSeparatedByCommas() {
//        assertThat(
//            indentedSerialise(
//                function(name = "f", arguments = listOf("x", "y"))
//            ),
//            equalTo(listOf(
//                "    def f(x, y):",
//                "        pass",
//                ""
//            ).joinToString("\n"))
//        )
//    }
//
//    @Test
//    fun expressionStatementSerialisation() {
//        assertThat(
//            indentedSerialise(
//                pythonExpressionStatement(pythonLiteralBoolean(true))
//            ),
//            equalTo("    True\n")
//        )
//    }
//
//    @Test
//    fun returnSerialisation() {
//        assertThat(
//            indentedSerialise(
//                pythonReturn(pythonLiteralBoolean(true))
//            ),
//            equalTo("    return True\n")
//        )
//    }
//
//    @Test
//    fun serialisingIfStatementWithBothBranches() {
//        assertThat(
//            indentedSerialise(
//                pythonIf(
//                    pythonLiteralBoolean(true),
//                    listOf(pythonReturn(pythonLiteralInt(0))),
//                    listOf(pythonReturn(pythonLiteralInt(1)))
//                )
//            ),
//            equalTo(listOf(
//                "    if True:",
//                "        return 0",
//                "    else:",
//                "        return 1",
//                ""
//            ).joinToString("\n"))
//        )
//    }
//
//    @Test
//    fun elseBranchIsMissingIfItHasNoStatements() {
//        assertThat(
//            indentedSerialise(
//                pythonIf(
//                    pythonLiteralBoolean(true),
//                    listOf(pythonReturn(pythonLiteralInt(0)))
//                )
//            ),
//            equalTo(listOf(
//                "    if True:",
//                "        return 0",
//                ""
//            ).joinToString("\n"))
//        )
//    }
//
//    @Test
//    fun trueBranchIsSerialisedAsPassWhenTrueBranchHasNoStatements() {
//        assertThat(
//            indentedSerialise(
//                pythonIf(
//                    pythonLiteralBoolean(true),
//                    listOf()
//                )
//            ),
//            equalTo(listOf(
//                "    if True:",
//                "        pass",
//                ""
//            ).joinToString("\n"))
//        )
//    }

    @Test
    fun booleanSerialisation() {
        assertThat(
            serialise(literalBool(true)),
            equalTo("true")
        )
        assertThat(
            serialise(literalBool(false)),
            equalTo("false")
        )
    }

    @Test
    fun integerSerialisation() {
        val node = literalInt(42)
        val output = serialise(node)
        assertThat(output, equalTo("42"))
    }

    data class StringTestCase(val name: String, val value: String, val expectedOutput: String)

    @TestFactory
    fun stringSerialisation(): List<DynamicTest> {
        return listOf(
            StringTestCase("empty string", "", "\"\""),
            StringTestCase("string with no special characters", "abc123", "\"abc123\""),
            StringTestCase("newline", "\n", "\"\\n\""),
            StringTestCase("carriage return", "\r", "\"\\r\""),
            StringTestCase("tab", "\t", "\"\\t\""),
            StringTestCase("double quote", "\"", "\"\\\"\""),
            StringTestCase("backslash", "\\", "\"\\\\\"")
        ).map({ case -> DynamicTest.dynamicTest(case.name, {
            val node = literalString(case.value)
            val output = serialise(node)
            assertThat(output, equalTo(case.expectedOutput))
        }) })
    }

    @Test
    fun variableReferenceSerialisation() {
        val node = variableReference("x")
        val output = serialise(node)
        assertThat(output, equalTo("x"))
    }
//
//    @TestFactory
//    fun binaryOperationSerialisation(): List<DynamicTest> {
//        return listOf(
//            Pair(PythonOperator.EQUALS, "x == y"),
//            Pair(PythonOperator.ADD, "x + y"),
//            Pair(PythonOperator.SUBTRACT, "x - y"),
//            Pair(PythonOperator.MULTIPLY, "x * y")
//        ).map({ operator -> DynamicTest.dynamicTest(operator.second, {
//            val node = pythonBinaryOperation(
//                operator = operator.first,
//                left = pythonVariableReference("x"),
//                right = pythonVariableReference("y")
//            )
//            val output = serialise(node)
//            assertThat(output, equalTo(operator.second))
//        })})
//    }
//
//    @Test
//    fun leftSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
//        val node = pythonBinaryOperation(
//            operator = PythonOperator.MULTIPLY,
//            left = pythonBinaryOperation(
//                PythonOperator.ADD,
//                pythonVariableReference("x"),
//                pythonVariableReference("y")
//            ),
//            right = pythonVariableReference("z")
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("(x + y) * z"))
//    }
//
//    @Test
//    fun rightSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
//        val node = pythonBinaryOperation(
//            operator = PythonOperator.MULTIPLY,
//            left = pythonVariableReference("x"),
//            right = pythonBinaryOperation(
//                PythonOperator.ADD,
//                pythonVariableReference("y"),
//                pythonVariableReference("z")
//            )
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("x * (y + z)"))
//    }
//
//    @Test
//    fun leftSubExpressionIsNotBracketedForLeftAssociativeOperators() {
//        val node = pythonBinaryOperation(
//            operator = PythonOperator.ADD,
//            left = pythonBinaryOperation(
//                PythonOperator.ADD,
//                pythonVariableReference("x"),
//                pythonVariableReference("y")
//            ),
//            right = pythonVariableReference("z")
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("x + y + z"))
//    }
//
//    @Test
//    fun rightSubExpressionIsBracketedForLeftAssociativeOperators() {
//        val node = pythonBinaryOperation(
//            operator = PythonOperator.ADD,
//            left = pythonVariableReference("x"),
//            right = pythonBinaryOperation(
//                PythonOperator.ADD,
//                pythonVariableReference("y"),
//                pythonVariableReference("z")
//            )
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("x + (y + z)"))
//    }
//
//    @Test
//    fun leftSubExpressionIsBracketedForChainedOperators() {
//        val node = pythonBinaryOperation(
//            operator = PythonOperator.EQUALS,
//            left = pythonBinaryOperation(
//                PythonOperator.EQUALS,
//                pythonVariableReference("x"),
//                pythonVariableReference("y")
//            ),
//            right = pythonVariableReference("z")
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("(x == y) == z"))
//    }
//
//    @Test
//    fun rightSubExpressionIsBracketedForChainedOperators() {
//        val node = pythonBinaryOperation(
//            operator = PythonOperator.EQUALS,
//            left = pythonVariableReference("x"),
//            right = pythonBinaryOperation(
//                PythonOperator.EQUALS,
//                pythonVariableReference("y"),
//                pythonVariableReference("z")
//            )
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("x == (y == z)"))
//    }
//
//    @Test
//    fun functionCallSerialisation() {
//        val node = pythonFunctionCall(
//            function = pythonVariableReference("f"),
//            arguments = listOf(
//                pythonVariableReference("x"),
//                pythonVariableReference("y")
//            )
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("f(x, y)"))
//    }
//
//    @Test
//    fun functionInFunctionCallIsNotBracketedWhenOfSamePrecedence() {
//        val node = pythonFunctionCall(
//            function = pythonFunctionCall(
//                pythonVariableReference("f"),
//                arguments = listOf()
//            ),
//            arguments = listOf()
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("f()()"))
//    }
//
//    @Test
//    fun functionInFunctionCallIsBracketedWhenOfLowerPrecedence() {
//        val node = pythonFunctionCall(
//            function = pythonBinaryOperation(
//                PythonOperator.ADD,
//                pythonVariableReference("f"),
//                pythonVariableReference("g")
//            ),
//            arguments = listOf()
//        )
//        val output = serialise(node)
//        assertThat(output, equalTo("(f + g)()"))
//    }
//
//    private fun indentedSerialise(node: FunctionNode): String {
//        return serialise(node, indentation = 1)
//    }
//
//    private fun indentedSerialise(node: StatementNode): String {
//        return serialise(node, indentation = 1)
//    }
}
