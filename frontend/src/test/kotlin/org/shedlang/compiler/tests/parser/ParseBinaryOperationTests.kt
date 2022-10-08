package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.frontend.parser.parseExpression

class ParseBinaryOperationTests {
    @TestFactory
    fun canParseOperator(): List<DynamicTest> {
        return listOf(
            Pair("==", BinaryOperator.EQUALS),
            Pair("!=", BinaryOperator.NOT_EQUAL),
            Pair("<", BinaryOperator.LESS_THAN),
            Pair("<=", BinaryOperator.LESS_THAN_OR_EQUAL),
            Pair(">", BinaryOperator.GREATER_THAN),
            Pair(">=", BinaryOperator.GREATER_THAN_OR_EQUAL),
            Pair("&&", BinaryOperator.AND),
            Pair("||", BinaryOperator.OR)
        ).map { (operatorString, operator) -> DynamicTest.dynamicTest("can parse $operatorString", {
            val source = "x $operatorString y"
            val node = parseString(::parseExpression, source)
            assertThat(node, isBinaryOperationNode(
                operator,
                isVariableReferenceNode("x"),
                isVariableReferenceNode("y")
            ))
        }) }
    }

    @Test
    fun canParseAddition() {
        val source = "x + y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.ADD,
            isVariableReferenceNode("x"),
            isVariableReferenceNode("y")
        ))
    }

    @Test
    fun canParseSubtraction() {
        val source = "x - y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.SUBTRACT,
            isVariableReferenceNode("x"),
            isVariableReferenceNode("y")
        ))
    }

    @Test
    fun canParseMultiplication() {
        val source = "x * y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.MULTIPLY,
            isVariableReferenceNode("x"),
            isVariableReferenceNode("y")
        ))
    }

    @Test
    fun canParseDivision() {
        val source = "x / y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.DIVIDE,
            isVariableReferenceNode("x"),
            isVariableReferenceNode("y")
        ))
    }

    @Test
    fun canParseLeftAssociativeOperationsWithThreeOperands() {
        val source = "x + y + z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.ADD,
            isBinaryOperationNode(
                BinaryOperator.ADD,
                isVariableReferenceNode("x"),
                isVariableReferenceNode("y")
            ),
            isVariableReferenceNode("z")
        ))
    }

    @Test
    fun canParseLeftAssociativeOperationsWithFourOperands() {
        val source = "a + b + c + d"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.ADD,
            isBinaryOperationNode(
                BinaryOperator.ADD,
                isBinaryOperationNode(
                    BinaryOperator.ADD,
                    isVariableReferenceNode("a"),
                    isVariableReferenceNode("b")
                ),
                isVariableReferenceNode("c")
            ),
            isVariableReferenceNode("d")
        ))
    }

    @Test
    fun higherPrecedenceOperatorsBindMoreTightlyThanLowerPrecedenceOperators() {
        val source = "x + y * z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.ADD,
            isVariableReferenceNode("x"),
            isBinaryOperationNode(
                BinaryOperator.MULTIPLY,
                isVariableReferenceNode("y"),
                isVariableReferenceNode("z")
            )

        ))
    }

    @Test
    fun canUseParensToGroupExpressions() {
        val source = "(x + y) * z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperationNode(
            BinaryOperator.MULTIPLY,
            isBinaryOperationNode(
                BinaryOperator.ADD,
                isVariableReferenceNode("x"),
                isVariableReferenceNode("y")
            ),
            isVariableReferenceNode("z")
        ))
    }
}
