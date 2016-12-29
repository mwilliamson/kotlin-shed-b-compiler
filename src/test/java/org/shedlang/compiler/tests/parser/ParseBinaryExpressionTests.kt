package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.BinaryOperationNode
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.ast.VariableReferenceNode
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.allOf

class ParseBinaryExpressionTests {
    @Test
    fun canParseEquality() {
        val source = "x == y"
        val node = parseString(::parseExpression, source)
        assertThat(node, cast(allOf(
            has(BinaryOperationNode::operator, equalTo(Operator.EQUALS)),
            has(BinaryOperationNode::left, isVariableReference("x")),
            has(BinaryOperationNode::right, isVariableReference("y"))
        )))
    }

    @Test
    fun canParseAddition() {
        val source = "x + y"
        val node = parseString(::parseExpression, source)
        assertThat(node, cast(allOf(
            has(BinaryOperationNode::operator, equalTo(Operator.ADD)),
            has(BinaryOperationNode::left, isVariableReference("x")),
            has(BinaryOperationNode::right, isVariableReference("y"))
        )))
    }

    @Test
    fun canParseSubtraction() {
        val source = "x - y"
        val node = parseString(::parseExpression, source)
        assertThat(node, cast(allOf(
            has(BinaryOperationNode::operator, equalTo(Operator.SUBTRACT)),
            has(BinaryOperationNode::left, isVariableReference("x")),
            has(BinaryOperationNode::right, isVariableReference("y"))
        )))
    }

    @Test
    fun canParseMultiplication() {
        val source = "x * y"
        val node = parseString(::parseExpression, source)
        assertThat(node, cast(allOf(
            has(BinaryOperationNode::operator, equalTo(Operator.MULTIPLY)),
            has(BinaryOperationNode::left, isVariableReference("x")),
            has(BinaryOperationNode::right, isVariableReference("y"))
        )))
    }

    private fun isVariableReference(name: String) = cast(has(VariableReferenceNode::name, equalTo(name)))
}
