package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isMap
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.literalInt

class ParseBinaryExpressionTests {
    @Test
    fun canParseEquality() {
        val source = "x == y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.EQUALS,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseAddition() {
        val source = "x + y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.ADD,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseSubtraction() {
        val source = "x - y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.SUBTRACT,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseMultiplication() {
        val source = "x * y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.MULTIPLY,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseFunctionCallWithNoArguments() {
        val source = "x()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isFunctionCall(
            isVariableReference("x"),
            isSequence()
        ))
    }

    @Test
    fun canParseFunctionCallWithOneArgument() {
        val source = "x(y)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isFunctionCall(
            isVariableReference("x"),
            isSequence(isVariableReference("y"))
        ))
    }

    @Test
    fun canParseFunctionCallWithManyArguments() {
        val source = "x(y, z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isFunctionCall(
            isVariableReference("x"),
            isSequence(isVariableReference("y"), isVariableReference("z"))
        ))
    }

    @Test
    fun canParseFunctionCallWithNamedArgument() {
        val source = "x(y=z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isFunctionCall(
            left = isVariableReference("x"),
            positionalArguments = isSequence(),
            namedArguments = isMap("y" to isVariableReference("z"))
        ))
    }

    @Test
    fun canParseLeftAssociativeOperationsWithThreeOperands() {
        val source = "x + y + z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.ADD,
            isBinaryOperation(
                Operator.ADD,
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
            Operator.ADD,
            isBinaryOperation(
                Operator.ADD,
                isBinaryOperation(
                    Operator.ADD,
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
            Operator.ADD,
            isVariableReference("x"),
            isBinaryOperation(
                Operator.MULTIPLY,
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
            Operator.MULTIPLY,
            isBinaryOperation(
                Operator.ADD,
                isVariableReference("x"),
                isVariableReference("y")
            ),
            isVariableReference("z")
        ))
    }
}
