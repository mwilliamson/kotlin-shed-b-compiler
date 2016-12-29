package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.BinaryOperationNode
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.ast.VariableReferenceNode
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.allOf

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

    private fun isBinaryOperation(
        operator: Operator,
        left: Matcher<ExpressionNode>,
        right: Matcher<ExpressionNode>
    ) : Matcher<ExpressionNode> {
        return cast(allOf(
            has(BinaryOperationNode::operator, equalTo(operator)),
            has(BinaryOperationNode::left, left),
            has(BinaryOperationNode::right, right)
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

    private fun isVariableReference(name: String) = cast(has(VariableReferenceNode::name, equalTo(name)))
}
