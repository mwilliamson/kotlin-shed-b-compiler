package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.parser.UnrecognisedEscapeSequenceError
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

    @TestFactory
    fun canParseStringLiteral(): List<DynamicTest> {
        fun testCase(name: String, source: String, value: String): DynamicTest {
            return DynamicTest.dynamicTest(name, {
                val node = parsePrimaryExpression(source)
                assertThat(node, cast(has(StringLiteralNode::value, equalTo(value))))
            })
        }

        return listOf(
            testCase("empty string", "\"\"", ""),
            testCase("string with normal characters", "\"abc\"", "abc"),
            testCase("escaped backslashes are decoded", "\"\\\\\"", "\\"),
            testCase("escaped double-quotes are decoded", "\"\\\"\"", "\""),
            testCase("escaped tabs are decoded", "\"\\t\"", "\t"),
            testCase("escaped newlines are decoded", "\"\\n\"", "\n"),
            testCase("escaped carriage returns are decoded", "\"\\r\"", "\r")
        )
    }

    @Test
    fun unrecognisedEscapeSequenceThrowsError() {
        val source = "\"a\\pb\""
        assertThat(
            { parsePrimaryExpression(source) },
            throws(has(UnrecognisedEscapeSequenceError::escapeSequence, equalTo("\\p")))
        )
    }

    private fun parsePrimaryExpression(source: String): ExpressionNode {
        return parseString(::tryParsePrimaryExpression, source)!!
    }
}
