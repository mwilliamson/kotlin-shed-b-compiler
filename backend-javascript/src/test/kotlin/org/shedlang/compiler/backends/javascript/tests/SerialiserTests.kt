package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.javascript.ast.JavascriptOperator
import org.shedlang.compiler.backends.javascript.ast.JavascriptStatementNode
import org.shedlang.compiler.backends.javascript.serialise

class SerialiserTests {
    @Test
    fun moduleSerialisation() {
        assertThat(
            serialise(jsModule(listOf(
                jsExpressionStatement(jsLiteralBool(true)),
                jsExpressionStatement(jsLiteralBool(false))
            ))),
            equalTo(listOf(
                "true;",
                "false;",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun emptyFunctionSerialisation() {
        assertThat(
            indentedSerialise(jsFunction(name = "f")),
            equalTo(listOf(
                "    function f() {",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun functionBodyIsSerialised() {
        assertThat(
            indentedSerialise(jsFunction(name = "f", body = listOf(
                jsReturn(jsLiteralInt(42))
            ))),
            equalTo(listOf(
                "    function f() {",
                "        return 42;",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun formalFunctionArgumentsAreSeparatedByCommas() {
        assertThat(
            indentedSerialise(
                jsFunction(name = "f", arguments = listOf("x", "y"))
            ),
            equalTo(listOf(
                "    function f(x, y) {",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun expressionStatementSerialisation() {
        assertThat(
            indentedSerialise(
                jsExpressionStatement(jsLiteralBool(true))
            ),
            equalTo("    true;\n")
        )
    }

    @Test
    fun returnSerialisation() {
        assertThat(
            indentedSerialise(
                jsReturn(jsLiteralBool(true))
            ),
            equalTo("    return true;\n")
        )
    }

    @Test
    fun serialisingIfStatementWithBothBranches() {
        assertThat(
            indentedSerialise(
                jsIfStatement(
                    jsLiteralBool(true),
                    listOf(jsReturn(jsLiteralInt(0))),
                    listOf(jsReturn(jsLiteralInt(1)))
                )
            ),
            equalTo(listOf(
                "    if (true) {",
                "        return 0;",
                "    } else {",
                "        return 1;",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun elseBranchIsMissingIfItHasNoStatements() {
        assertThat(
            indentedSerialise(
                jsIfStatement(
                    jsLiteralBool(true),
                    listOf(jsReturn(jsLiteralInt(0)))
                )
            ),
            equalTo(listOf(
                "    if (true) {",
                "        return 0;",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun trueBranchIsSerialisedAsEmptyWhenTrueBranchHasNoStatements() {
        assertThat(
            indentedSerialise(
                jsIfStatement(
                    jsLiteralBool(true),
                    listOf()
                )
            ),
            equalTo(listOf(
                "    if (true) {",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun booleanSerialisation() {
        assertThat(
            serialise(jsLiteralBool(true)),
            equalTo("true")
        )
        assertThat(
            serialise(jsLiteralBool(false)),
            equalTo("false")
        )
    }

    @Test
    fun integerSerialisation() {
        val node = jsLiteralInt(42)
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
            val node = jsLiteralString(case.value)
            val output = serialise(node)
            assertThat(output, equalTo(case.expectedOutput))
        }) })
    }

    @Test
    fun variableReferenceSerialisation() {
        val node = jsVariableReference("x")
        val output = serialise(node)
        assertThat(output, equalTo("x"))
    }

    @TestFactory
    fun binaryOperationSerialisation(): List<DynamicTest> {
        return listOf(
            Pair(JavascriptOperator.EQUALS, "x === y"),
            Pair(JavascriptOperator.ADD, "x + y"),
            Pair(JavascriptOperator.SUBTRACT, "x - y"),
            Pair(JavascriptOperator.MULTIPLY, "x * y")
        ).map({ operator -> DynamicTest.dynamicTest(operator.second, {
            val node = jsBinaryOperation(
                operator = operator.first,
                left = jsVariableReference("x"),
                right = jsVariableReference("y")
            )
            val output = serialise(node)
            assertThat(output, equalTo(operator.second))
        })})
    }

    @Test
    fun leftSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
        val node = jsBinaryOperation(
            operator = JavascriptOperator.MULTIPLY,
            left = jsBinaryOperation(
                JavascriptOperator.ADD,
                jsVariableReference("x"),
                jsVariableReference("y")
            ),
            right = jsVariableReference("z")
        )
        val output = serialise(node)
        assertThat(output, equalTo("(x + y) * z"))
    }

    @Test
    fun rightSubExpressionOfBinaryOperationIsBracketedWhenPrecedenceIsLessThanOuterOperator() {
        val node = jsBinaryOperation(
            operator = JavascriptOperator.MULTIPLY,
            left = jsVariableReference("x"),
            right = jsBinaryOperation(
                JavascriptOperator.ADD,
                jsVariableReference("y"),
                jsVariableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x * (y + z)"))
    }

    @Test
    fun leftSubExpressionIsNotBracketedForLeftAssociativeOperators() {
        val node = jsBinaryOperation(
            operator = JavascriptOperator.ADD,
            left = jsBinaryOperation(
                JavascriptOperator.ADD,
                jsVariableReference("x"),
                jsVariableReference("y")
            ),
            right = jsVariableReference("z")
        )
        val output = serialise(node)
        assertThat(output, equalTo("x + y + z"))
    }

    @Test
    fun rightSubExpressionIsBracketedForLeftAssociativeOperators() {
        val node = jsBinaryOperation(
            operator = JavascriptOperator.ADD,
            left = jsVariableReference("x"),
            right = jsBinaryOperation(
                JavascriptOperator.ADD,
                jsVariableReference("y"),
                jsVariableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x + (y + z)"))
    }

    @Test
    fun functionCallSerialisation() {
        val node = jsFunctionCall(
            function = jsVariableReference("f"),
            arguments = listOf(
                jsVariableReference("x"),
                jsVariableReference("y")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("f(x, y)"))
    }

    @Test
    fun functionInFunctionCallIsNotBracketedWhenOfSamePrecedence() {
        val node = jsFunctionCall(
            function = jsFunctionCall(
                jsVariableReference("f"),
                arguments = listOf()
            ),
            arguments = listOf()
        )
        val output = serialise(node)
        assertThat(output, equalTo("f()()"))
    }

    @Test
    fun functionInFunctionCallIsBracketedWhenOfLowerPrecedence() {
        val node = jsFunctionCall(
            function = jsBinaryOperation(
                JavascriptOperator.ADD,
                jsVariableReference("f"),
                jsVariableReference("g")
            ),
            arguments = listOf()
        )
        val output = serialise(node)
        assertThat(output, equalTo("(f + g)()"))
    }

    private fun indentedSerialise(node: JavascriptStatementNode): String {
        return serialise(node, indentation = 1)
    }
}
