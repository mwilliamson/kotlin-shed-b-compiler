package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.parser.parseExpression

class ParseBinaryOperationTests {
    @TestFactory
    fun canParseOperator(): List<DynamicTest> {
        return listOf(
            Pair("==", BinaryOperator.EQUALS),
            Pair("<", BinaryOperator.LESS_THAN),
            Pair("<=", BinaryOperator.LESS_THAN_OR_EQUAL),
            Pair(">", BinaryOperator.GREATER_THAN),
            Pair(">=", BinaryOperator.GREATER_THAN_OR_EQUAL),
            Pair("&&", BinaryOperator.AND),
            Pair("||", BinaryOperator.OR)
        ).map { (operatorString, operator) -> DynamicTest.dynamicTest("can parse $operatorString", {
            val source = "x $operatorString y"
            val node = parseString(::parseExpression, source)
            assertThat(node, isBinaryOperation(
                operator,
                isVariableReference("x"),
                isVariableReference("y")
            ))
        }) }
    }

    @Test
    fun canParseAddition() {
        val source = "x + y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            BinaryOperator.ADD,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseSubtraction() {
        val source = "x - y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            BinaryOperator.SUBTRACT,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseMultiplication() {
        val source = "x * y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            BinaryOperator.MULTIPLY,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseLeftAssociativeOperationsWithThreeOperands() {
        val source = "x + y + z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            BinaryOperator.ADD,
            isBinaryOperation(
                BinaryOperator.ADD,
                isVariableReference("x"),
                isVariableReference("y")
            ),
            isVariableReference("z")
        ))
    }

    @Test
    fun canParseLeftAssociativeOperationsWithFourOperands() {
        val source = "a + b + c + d"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            BinaryOperator.ADD,
            isBinaryOperation(
                BinaryOperator.ADD,
                isBinaryOperation(
                    BinaryOperator.ADD,
                    isVariableReference("a"),
                    isVariableReference("b")
                ),
                isVariableReference("c")
            ),
            isVariableReference("d")
        ))
    }

    @Test
    fun higherPrecedenceOperatorsBindMoreTightlyThanLowerPrecedenceOperators() {
        val source = "x + y * z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            BinaryOperator.ADD,
            isVariableReference("x"),
            isBinaryOperation(
                BinaryOperator.MULTIPLY,
                isVariableReference("y"),
                isVariableReference("z")
            )

        ))
    }

    @Test
    fun canUseParensToGroupExpressions() {
        val source = "(x + y) * z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            BinaryOperator.MULTIPLY,
            isBinaryOperation(
                BinaryOperator.ADD,
                isVariableReference("x"),
                isVariableReference("y")
            ),
            isVariableReference("z")
        ))
    }
}
