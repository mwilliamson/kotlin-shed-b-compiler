package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.backends.javascript.serialise
import org.shedlang.compiler.tests.typechecker.*

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

    @TestFactory
    fun binaryOperationSerialisation(): List<DynamicTest> {
        return listOf(
            Pair(Operator.EQUALS, "x === y"),
            Pair(Operator.ADD, "x + y"),
            Pair(Operator.SUBTRACT, "x - y"),
            Pair(Operator.MULTIPLY, "x * y")
        ).map({ operator -> DynamicTest.dynamicTest(operator.second, {
            val node = binaryOperation(
                operator = operator.first,
                left = variableReference("x"),
                right = variableReference("y")
            )
            val output = serialise(node)
            assertThat(output, equalTo(operator.second))
        })})
    }

    @Test
    fun leftSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
        val node = binaryOperation(
            operator = Operator.MULTIPLY,
            left = binaryOperation(
                Operator.ADD,
                variableReference("x"),
                variableReference("y")
            ),
            right = variableReference("z")
        )
        val output = serialise(node)
        assertThat(output, equalTo("(x + y) * z"))
    }

    @Test
    fun rightSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
        val node = binaryOperation(
            operator = Operator.MULTIPLY,
            left = variableReference("x"),
            right = binaryOperation(
                Operator.ADD,
                variableReference("y"),
                variableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x * (y + z)"))
    }

    @Test
    fun leftSubExpressionIsNotBracketedForLeftAssociativeOperators() {
        val node = binaryOperation(
            operator = Operator.ADD,
            left = binaryOperation(
                Operator.ADD,
                variableReference("x"),
                variableReference("y")
            ),
            right = variableReference("z")
        )
        val output = serialise(node)
        assertThat(output, equalTo("x + y + z"))
    }

    @Test
    fun rightSubExpressionIsBracketedForLeftAssociativeOperators() {
        val node = binaryOperation(
            operator = Operator.ADD,
            left = variableReference("x"),
            right = binaryOperation(
                Operator.ADD,
                variableReference("y"),
                variableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x + (y + z)"))
    }

    @Test
    fun functionCallSerialisation() {
        val node = functionCall(
            function = variableReference("f"),
            arguments = listOf(
                variableReference("x"),
                variableReference("y")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("f(x, y)"))
    }

    @Test
    fun functionInFunctionCallIsNotBracketedWhenOfSamePrecedence() {
        val node = functionCall(
            function = functionCall(
                variableReference("f"),
                arguments = listOf()
            ),
            arguments = listOf()
        )
        val output = serialise(node)
        assertThat(output, equalTo("f()()"))
    }

    @Test
    fun functionInFunctionCallIsBracketedWhenOfLowerPrecedence() {
        val node = functionCall(
            function = binaryOperation(
                Operator.ADD,
                variableReference("f"),
                variableReference("g")
            ),
            arguments = listOf()
        )
        val output = serialise(node)
        assertThat(output, equalTo("(f + g)()"))
    }
//
//    private fun indentedSerialise(node: FunctionNode): String {
//        return serialise(node, indentation = 1)
//    }
//
//    private fun indentedSerialise(node: StatementNode): String {
//        return serialise(node, indentation = 1)
//    }
}
