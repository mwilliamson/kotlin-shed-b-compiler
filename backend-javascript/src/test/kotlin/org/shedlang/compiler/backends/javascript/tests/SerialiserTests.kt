package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.javascript.ast.JavascriptBinaryOperator
import org.shedlang.compiler.backends.javascript.ast.JavascriptExpressionNode
import org.shedlang.compiler.backends.javascript.ast.JavascriptStatementNode
import org.shedlang.compiler.backends.javascript.ast.JavascriptUnaryOperator
import org.shedlang.compiler.backends.javascript.serialise
import org.shedlang.compiler.backends.javascript.serialiseTarget

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
    fun whenFunctionIsAsyncThenAsyncKeywordIsPrepended() {
        assertThat(
            indentedSerialise(jsFunction(name = "f", isAsync = true)),
            equalTo(listOf(
                "    async function f() {",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun functionBodyIsSerialised() {
        assertThat(
            indentedSerialise(jsFunction(name = "f", body = listOf(
                jsReturn(jsLiteralBool(true))
            ))),
            equalTo(listOf(
                "    function f() {",
                "        return true;",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun functionParametersAreSeparatedByCommas() {
        assertThat(
            indentedSerialise(
                jsFunction(name = "f", parameters = listOf("x", "y"))
            ),
            equalTo(listOf(
                "    function f(x, y) {",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun anonymousFunctionExpressionSerialisation() {
        assertThat(
            indentedSerialise(jsFunctionExpression(
                parameters = listOf(),
                body = listOf()
            )),
            equalTo(listOf(
                "(function () {",
                "    })"
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
    fun serialisingIfStatementWithSingleConditionalBranchAndElseBranch() {
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
                "        return 0n;",
                "    } else {",
                "        return 1n;",
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
                "        return 0n;",
                "    }",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun additionalConditionalBranchesAreSerialisedUsingElseIf() {
        assertThat(
            indentedSerialise(
                jsIfStatement(
                    conditionalBranches = listOf(
                        jsConditionalBranch(
                            condition = jsVariableReference("x"),
                            body = listOf(jsReturn(jsLiteralInt(0)))
                        ),
                        jsConditionalBranch(
                            condition = jsVariableReference("y"),
                            body = listOf(jsReturn(jsLiteralInt(1)))
                        ),
                        jsConditionalBranch(
                            condition = jsVariableReference("z"),
                            body = listOf(jsReturn(jsLiteralInt(2)))
                        )
                    )
                )
            ),
            equalTo(listOf(
                "    if (x) {",
                "        return 0n;",
                "    } else if (y) {",
                "        return 1n;",
                "    } else if (z) {",
                "        return 2n;",
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
    fun constSerialisation() {
        assertThat(
            indentedSerialise(
                jsConst("x", jsLiteralBool(true))
            ),
            equalTo("    const x = true;\n")
        )
    }

    @Test
    fun nullSerialisation() {
        assertThat(
            serialise(jsLiteralNull()),
            equalTo("null")
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
        assertThat(output, equalTo("42n"))
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

    @Test
    fun notOperationSerialisation() {
        val node = jsUnaryOperation(
            operator = JavascriptUnaryOperator.NOT,
            operand = jsVariableReference("x")
        )
        val output = serialise(node)
        assertThat(output, equalTo("!x"))
    }

    @Test
    fun unaryMinusSerialisation() {
        val node = jsUnaryOperation(
            operator = JavascriptUnaryOperator.MINUS,
            operand = jsVariableReference("x")
        )
        val output = serialise(node)
        assertThat(output, equalTo("-x"))
    }

    @Test
    fun awaitSerialisation() {
        val node = jsAwait(expression = jsVariableReference("x"))
        val output = serialise(node)
        assertThat(output, equalTo("await x"))
    }

    @Test
    fun unaryOperationsCanBeRepeatedWithoutParens() {
        val node = jsUnaryOperation(
            operator = JavascriptUnaryOperator.NOT,
            operand = jsUnaryOperation(
                operator = JavascriptUnaryOperator.NOT,
                operand = jsVariableReference("x")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("!!x"))
    }

    @TestFactory
    fun binaryOperationSerialisation(): List<DynamicTest> {
        return listOf(
            Pair(JavascriptBinaryOperator.EQUALS, "x === y"),
            Pair(JavascriptBinaryOperator.NOT_EQUAL, "x !== y"),
            Pair(JavascriptBinaryOperator.LESS_THAN, "x < y"),
            Pair(JavascriptBinaryOperator.LESS_THAN_OR_EQUAL, "x <= y"),
            Pair(JavascriptBinaryOperator.GREATER_THAN, "x > y"),
            Pair(JavascriptBinaryOperator.GREATER_THAN_OR_EQUAL, "x >= y"),
            Pair(JavascriptBinaryOperator.ADD, "x + y"),
            Pair(JavascriptBinaryOperator.SUBTRACT, "x - y"),
            Pair(JavascriptBinaryOperator.MULTIPLY, "x * y"),
            Pair(JavascriptBinaryOperator.AND, "x && y"),
            Pair(JavascriptBinaryOperator.OR, "x || y")
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
            operator = JavascriptBinaryOperator.MULTIPLY,
            left = jsBinaryOperation(
                JavascriptBinaryOperator.ADD,
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
            operator = JavascriptBinaryOperator.MULTIPLY,
            left = jsVariableReference("x"),
            right = jsBinaryOperation(
                JavascriptBinaryOperator.ADD,
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
            operator = JavascriptBinaryOperator.ADD,
            left = jsBinaryOperation(
                JavascriptBinaryOperator.ADD,
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
            operator = JavascriptBinaryOperator.ADD,
            left = jsVariableReference("x"),
            right = jsBinaryOperation(
                JavascriptBinaryOperator.ADD,
                jsVariableReference("y"),
                jsVariableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x + (y + z)"))
    }

    @Test
    fun whenOperandsHaveHigherPrecedenceThenConditionalOperationSerialisesOperandsWithoutBrackets() {
        val node = jsConditionalOperation(
            condition = jsVariableReference("condition"),
            trueExpression = jsVariableReference("x"),
            falseExpression = jsVariableReference("y")
        )
        val output = serialise(node)
        assertThat(output, equalTo("condition ? x : y"))
    }

    @Test
    fun whenOperandsHaveSamePrecedenceThenConditionalOperationSerialisesFalseExpressionWithoutBrackets() {
        val node = jsConditionalOperation(
            condition = jsVariableReference("condition1"),
            trueExpression = jsConditionalOperation(
                condition = jsVariableReference("condition2"),
                trueExpression = jsVariableReference("a"),
                falseExpression = jsVariableReference("b")
            ),
            falseExpression = jsConditionalOperation(
                condition = jsVariableReference("condition3"),
                trueExpression = jsVariableReference("c"),
                falseExpression = jsVariableReference("d")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("condition1 ? (condition2 ? a : b) : condition3 ? c : d"))
    }

    @Test
    fun whenOperandsHaveLowerPrecedenceThenConditionalOperationSerialisesOperandsWithBrackets() {
        val node = jsConditionalOperation(
            condition = jsVariableReference("condition1"),
            trueExpression = jsAssign(
                target = jsVariableReference("target1"),
                expression = jsVariableReference("source1")
            ),
            falseExpression = jsAssign(
                target = jsVariableReference("target2"),
                expression = jsVariableReference("source2")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("condition1 ? (target1 = source1) : (target2 = source2)"))
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
                JavascriptBinaryOperator.ADD,
                jsVariableReference("f"),
                jsVariableReference("g")
            ),
            arguments = listOf()
        )
        val output = serialise(node)
        assertThat(output, equalTo("(f + g)()"))
    }

    @Test
    fun propertyAccessSerialisation() {
        val node = jsPropertyAccess(
            receiver = jsVariableReference("x"),
            propertyName = "y"
        )
        val output = serialise(node)
        assertThat(output, equalTo("x.y"))
    }

    @Test
    fun emptyArraySerialisation() {
        val node = jsArray(listOf())
        val output = serialise(node)
        assertThat(output, equalTo("[]"))
    }

    @Test
    fun singletonArraySerialisation() {
        val node = jsArray(listOf(jsLiteralBool(true)))
        val output = serialise(node)
        assertThat(output, equalTo("[true]"))
    }

    @Test
    fun multipleElementArraySerialisation() {
        val node = jsArray(listOf(jsLiteralBool(true), jsLiteralBool(false)))
        val output = serialise(node)
        assertThat(output, equalTo("[true, false]"))
    }

    @Test
    fun emptyObjectLiteralSerialisation() {
        val node = jsObject(listOf())
        val output = serialise(node)
        assertThat(output, equalTo("{}"))
    }

    @Test
    fun singlePropertyObjectLiteralSerialisation() {
        val node = jsObject(listOf(jsProperty("x", jsLiteralBool(true))))
        val output = indentedSerialise(node)
        assertThat(output, equalTo(listOf(
            "{",
            "        x: true",
            "    }"
        ).joinToString("\n")))
    }

    @Test
    fun multiplePropertyObjectLiteralSerialisation() {
        val node = jsObject(listOf(jsProperty("x", jsLiteralBool(true)), jsProperty("y", jsLiteralBool(false))))
        val output = indentedSerialise(node)
        assertThat(output, equalTo(listOf(
            "{",
            "        x: true,",
            "        y: false",
            "    }"
        ).joinToString("\n")))
    }

    @Test
    fun emptyArrayDestructuringSerialisation() {
        val node = jsArrayDestructuring(listOf())
        val output = serialiseTarget(node, indentation = 0)
        assertThat(output, equalTo("[]"))
    }

    @Test
    fun singletonArrayDestructuringSerialisation() {
        val node = jsArrayDestructuring(listOf(jsVariableReference("x")))
        val output = serialiseTarget(node, indentation = 0)
        assertThat(output, equalTo("[x]"))
    }

    @Test
    fun multipleElementArrayDestructuringSerialisation() {
        val node = jsArrayDestructuring(listOf(jsVariableReference("x"), jsVariableReference("y")))
        val output = serialiseTarget(node, indentation = 0)
        assertThat(output, equalTo("[x, y]"))
    }

    @Test
    fun emptyObjectDestructuringSerialisation() {
        val node = jsObjectDestructuring(listOf())
        val output = serialiseTarget(node, indentation = 0)
        assertThat(output, equalTo("{}"))
    }

    @Test
    fun singletonObjectDestructuringSerialisation() {
        val node = jsObjectDestructuring(listOf("x" to jsVariableReference("targetX")))
        val output = serialiseTarget(node, indentation = 0)
        assertThat(output, equalTo("{\n    x: targetX\n}"))
    }

    @Test
    fun multiplePropertyObjectDestructuringSerialisation() {
        val node = jsObjectDestructuring(listOf("x" to jsVariableReference("targetX"), "y" to jsVariableReference("targetY")))
        val output = serialiseTarget(node, indentation = 0)
        assertThat(output, equalTo("{\n    x: targetX,\n    y: targetY\n}"))
    }

    private fun indentedSerialise(node: JavascriptStatementNode): String {
        return serialise(node, indentation = 1)
    }

    private fun indentedSerialise(node: JavascriptExpressionNode): String {
        return serialise(node, indentation = 1)
    }

    private fun serialise(node: JavascriptExpressionNode): String {
        return serialise(node, indentation = 0)
    }
}
