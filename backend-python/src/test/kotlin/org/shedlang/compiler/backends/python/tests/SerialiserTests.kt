package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.python.ast.PythonFunctionNode
import org.shedlang.compiler.backends.python.ast.PythonOperator
import org.shedlang.compiler.backends.python.ast.PythonStatementNode
import org.shedlang.compiler.backends.python.serialise

class SerialiserTests {
    @Test
    fun moduleSerialisation() {
        assertThat(
            serialise(pythonModule(listOf(
                pythonExpressionStatement(pythonLiteralBoolean(true)),
                pythonExpressionStatement(pythonLiteralBoolean(false))
            ))),
            equalTo(listOf(
                "True",
                "False",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun importFromSerialisation() {
        val node = pythonImportFrom(
            module = "a.b.c",
            names = listOf("d" to "d", "e" to "f")
        )
        assertThat(
            serialise(node),
            equalTo("from a.b.c import d, e as f\n")
        )
    }

    @Test
    fun emptyFunctionSerialisation() {
        assertThat(
            indentedSerialise(pythonFunction(name = "f")),
            equalTo(listOf(
                "    def f():",
                "        pass",
                "",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun functionBodyIsSerialised() {
        assertThat(
            indentedSerialise(pythonFunction(name = "f", body = listOf(
                pythonReturn(pythonLiteralInt(42))
            ))),
            equalTo(listOf(
                "    def f():",
                "        return 42",
                "",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun nestedFunctionsDoNotAccumulateTrailingNewlines() {
        assertThat(
            indentedSerialise(pythonFunction(name = "f", body = listOf(
                pythonFunction(name="g", body = listOf())
            ))),
            equalTo(listOf(
                "    def f():",
                "        def g():",
                "            pass",
                "",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun emptyClassSerialisation() {
        assertThat(
            indentedSerialise(pythonClass(name = "X")),
            equalTo(listOf(
                "    class X(object):",
                "        pass",
                "",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun classBodyIsSerialised() {
        assertThat(
            indentedSerialise(pythonClass(name = "X", body = listOf(
                pythonExpressionStatement(pythonLiteralBoolean(true))
            ))),
            equalTo(listOf(
                "    class X(object):",
                "        True",
                "",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun functionParametersAreSeparatedByCommas() {
        assertThat(
            indentedSerialise(
                pythonFunction(name = "f", parameters = listOf("x", "y"))
            ),
            equalTo(listOf(
                "    def f(x, y):",
                "        pass",
                "",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun expressionStatementSerialisation() {
        assertThat(
            indentedSerialise(
                pythonExpressionStatement(pythonLiteralBoolean(true))
            ),
            equalTo("    True\n")
        )
    }

    @Test
    fun returnSerialisation() {
        assertThat(
            indentedSerialise(
                pythonReturn(pythonLiteralBoolean(true))
            ),
            equalTo("    return True\n")
        )
    }

    @Test
    fun serialisingIfStatementWithSingleConditionalBranchAndElseBranch() {
        assertThat(
            indentedSerialise(
                pythonIf(
                    pythonLiteralBoolean(true),
                    listOf(pythonReturn(pythonLiteralInt(0))),
                    listOf(pythonReturn(pythonLiteralInt(1)))
                )
            ),
            equalTo(listOf(
                "    if True:",
                "        return 0",
                "    else:",
                "        return 1",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun elseBranchIsMissingIfItHasNoStatements() {
        assertThat(
            indentedSerialise(
                pythonIf(
                    pythonLiteralBoolean(true),
                    listOf(pythonReturn(pythonLiteralInt(0)))
                )
            ),
            equalTo(listOf(
                "    if True:",
                "        return 0",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun additionalConditionalBranchesAreSerialisedUsingElif() {
        assertThat(
            indentedSerialise(
                pythonIf(
                    listOf(
                        pythonConditionalBranch(
                            pythonVariableReference("x"),
                            listOf(pythonReturn(pythonLiteralInt(0)))
                        ),
                        pythonConditionalBranch(
                            pythonVariableReference("y"),
                            listOf(pythonReturn(pythonLiteralInt(1)))
                        ),
                        pythonConditionalBranch(
                            pythonVariableReference("z"),
                            listOf(pythonReturn(pythonLiteralInt(2)))
                        )
                    )
                )
            ),
            equalTo(listOf(
                "    if x:",
                "        return 0",
                "    elif y:",
                "        return 1",
                "    elif z:",
                "        return 2",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun trueBranchIsSerialisedAsPassWhenTrueBranchHasNoStatements() {
        assertThat(
            indentedSerialise(
                pythonIf(
                    pythonLiteralBoolean(true),
                    listOf()
                )
            ),
            equalTo(listOf(
                "    if True:",
                "        pass",
                ""
            ).joinToString("\n"))
        )
    }

    @Test
    fun assignmentSerialisation() {
        assertThat(
            indentedSerialise(
                pythonAssignment(
                    target = pythonVariableReference("x"),
                    expression = pythonLiteralBoolean(true)
                )
            ),
            equalTo("    x = True\n")
        )
    }

    @Test
    fun whileSerialisation() {
        assertThat(
            indentedSerialise(
                pythonWhile(
                    condition = pythonVariableReference("x"),
                    body = listOf(pythonReturn(pythonVariableReference("y")))
                )
            ),
            equalTo("    while x:\n        return y\n")
        )
    }

    @Test
    fun noneSerialisation() {
        assertThat(
            serialise(pythonNone()),
            equalTo("None")
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
            val node = pythonLiteralString(case.value)
            val output = serialise(node)
            assertThat(output, equalTo(case.expectedOutput))
        }) })
    }

    @Test
    fun variableReferenceSerialisation() {
        val node = pythonVariableReference("x")
        val output = serialise(node)
        assertThat(output, equalTo("x"))
    }

    @Test
    fun emptyTuplesAreSerialisedAsEmptyParens() {
        val node = pythonTuple()
        val output = serialise(node)
        assertThat(output, equalTo("()"))
    }

    @Test
    fun singletonTupleIsSerialisedWithTrailingComma() {
        val node = pythonTuple(pythonLiteralInt(0))
        val output = serialise(node)
        assertThat(output, equalTo("(0, )"))
    }

    @Test
    fun tuplesWithMultipleElementsSeparateElementsWithComma() {
        val node = pythonTuple(pythonLiteralInt(0), pythonLiteralInt(1))
        val output = serialise(node)
        assertThat(output, equalTo("(0, 1)"))
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
    fun leftSubExpressionIsBracketedForChainedOperators() {
        val node = pythonBinaryOperation(
            operator = PythonOperator.EQUALS,
            left = pythonBinaryOperation(
                PythonOperator.EQUALS,
                pythonVariableReference("x"),
                pythonVariableReference("y")
            ),
            right = pythonVariableReference("z")
        )
        val output = serialise(node)
        assertThat(output, equalTo("(x == y) == z"))
    }

    @Test
    fun rightSubExpressionIsBracketedForChainedOperators() {
        val node = pythonBinaryOperation(
            operator = PythonOperator.EQUALS,
            left = pythonVariableReference("x"),
            right = pythonBinaryOperation(
                PythonOperator.EQUALS,
                pythonVariableReference("y"),
                pythonVariableReference("z")
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("x == (y == z)"))
    }

    @Test
    fun functionCallSerialisation() {
        val node = pythonFunctionCall(
            function = pythonVariableReference("f"),
            arguments = listOf(
                pythonVariableReference("x"),
                pythonVariableReference("y")
            ),
            keywordArguments = listOf(
                "z" to pythonLiteralInt(1)
            )
        )
        val output = serialise(node)
        assertThat(output, equalTo("f(x, y, z=1)"))
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

    @Test
    fun attributeAccessSerialisation() {
        val node = pythonAttributeAccess(
            receiver = pythonVariableReference("x"),
            attributeName = "y"
        )
        val output = serialise(node)
        assertThat(output, equalTo("x.y"))
    }

    @Test
    fun lambdaSerialisation() {
        val node = pythonLambda(
            parameters = listOf("x", "y"),
            body = pythonLiteralBoolean(true)
        )
        val output = serialise(node)
        assertThat(output, equalTo("lambda x, y: True"))
    }

    private fun indentedSerialise(node: PythonFunctionNode): String {
        return serialise(node, indentation = 1)
    }

    private fun indentedSerialise(node: PythonStatementNode): String {
        return serialise(node, indentation = 1)
    }
}
