package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.BooleanLiteralNode
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.IntegerLiteralNode
import org.shedlang.compiler.ast.VariableReferenceNode
import org.shedlang.compiler.parser.tryParsePrimaryExpression

class ParsePrimaryExpressionTests {
    @Test
    fun integerTokenCanBeParsedAsIntegerLiteral() {
        val source = "1"
        val node = parsePrimaryExpression(source)
        assertThat(node, cast(has(IntegerLiteralNode::value, equalTo(1))))
    }

    @TestFactory
    fun booleanKeywordCanBeParsedAsBooleanLiteral(): List<DynamicTest> {
        fun generateTest(source: String, value: Boolean): DynamicTest {
            return DynamicTest.dynamicTest(source, {
                val node = parsePrimaryExpression(source)
                assertThat(node, cast(has(BooleanLiteralNode::value, equalTo(value))))
            })
        }

        return listOf(generateTest("true", true), generateTest("false", false))
    }

    @Test
    fun identifierCanBeParsedAsVariableReference() {
        val source = "x"
        val node = parsePrimaryExpression(source)
        assertThat(node, cast(has(VariableReferenceNode::name, equalTo("x"))))
    }

    private fun parsePrimaryExpression(source: String): ExpressionNode {
        return parseString(::tryParsePrimaryExpression, source)!!
    }
}
