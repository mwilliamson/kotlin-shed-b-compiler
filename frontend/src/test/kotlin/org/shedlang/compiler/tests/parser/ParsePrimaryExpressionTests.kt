package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.parser.InvalidCharacter
import org.shedlang.compiler.parser.InvalidCharacterLiteral
import org.shedlang.compiler.parser.UnrecognisedEscapeSequenceError
import org.shedlang.compiler.parser.tryParsePrimaryExpression
import org.shedlang.compiler.tests.allOf

class ParsePrimaryExpressionTests {
    @Test
    fun unitKeywordCanBeParsedAsIntegerLiteral() {
        val source = "unit"
        val node = parsePrimaryExpression(source)
        assertThat(node, isA<UnitLiteralNode>())
    }

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
        assertThat(node, cast(has(VariableReferenceNode::name, isIdentifier("x"))))
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
            testCase("escaped carriage returns are decoded", "\"\\r\"", "\r"),
            testCase("hexadecimal unicode escape sequences are decoded", "\"\\u{1B}\"", "\u001B")
        )
    }

    @Test
    fun whenUnicodeEscapeSequenceInStringIsMissingOpeningBraceThenErrorIsThrown() {
        assertThat(
            { parsePrimaryExpression("\"\\u001B\"") },
            throws(allOf(
                has(InvalidCharacter::source, isStringSource(
                    contents = "\"\\u001B\"",
                    index = 3
                )),
                has(InvalidCharacter::message, equalTo("Expected opening brace"))
            ))
        )
    }

    @Test
    fun whenUnicodeEscapeSequenceInStringIsMissingClosingBraceThenErrorIsThrown() {
        assertThat(
            { parsePrimaryExpression("\"\\u{1B\"") },
            throws(allOf(
                has(InvalidCharacter::source, isStringSource(
                    contents = "\"\\u{1B\"",
                    index = 3
                )),
                has(InvalidCharacter::message, equalTo("Could not find closing brace"))
            ))
        )
    }

    @Test
    fun unicodeEscapeSequenceErrorIndexIsRelativeToEntireSource() {
        assertThat(
            { parsePrimaryExpression("  \"\\u001B\"") },
            throws(allOf(
                has(InvalidCharacter::source, isStringSource(
                    contents = "  \"\\u001B\"",
                    index = 5
                )),
                has(InvalidCharacter::message, equalTo("Expected opening brace"))
            ))
        )
    }

    @TestFactory
    fun canParseCharacterLiteral(): List<DynamicTest> {
        fun testCase(name: String, source: String, value: Int): DynamicTest {
            return DynamicTest.dynamicTest(name, {
                val node = parsePrimaryExpression(source)
                assertThat(node, cast(has(CharacterLiteralNode::value, equalTo(value))))
            })
        }

        return listOf(
            testCase("normal character", "'a'", 'a'.toInt()),
            testCase("escaped backslash is decoded", "'\\\\'", '\\'.toInt()),
            testCase("escaped single-quote is decoded", "'\\''", '\''.toInt()),
            testCase("escaped tab is decoded", "'\\t'", '\t'.toInt()),
            testCase("escaped newline is decoded", "'\\n'", '\n'.toInt()),
            testCase("escaped carriage return is decoded", "'\\r'", '\r'.toInt()),
            testCase("hexadecimal unicode escape sequence is decoded", "'\\u{1B}'", '\u001B'.toInt())
        )
    }

    @Test
    fun whenUnicodeEscapeSequenceInCharacterIsMissingOpeningBraceThenErrorIsThrown() {
        assertThat(
            { parsePrimaryExpression("'\\u001B'") },
            throws(allOf(
                has(InvalidCharacter::source, isStringSource(
                    contents = "'\\u001B'",
                    index = 3
                )),
                has(InvalidCharacter::message, equalTo("Expected opening brace"))
            ))
        )
    }

    @Test
    fun whenCharacterLiteralHasMultipleCharactersThenErrorIsThrown() {
        assertThat(
            { parsePrimaryExpression("'ab'") },
            throws(allOf(
                has(InvalidCharacterLiteral::source, isStringSource(
                    contents = "'ab'",
                    index = 0
                )),
                has(InvalidCharacterLiteral::message, equalTo("Character literal has 2 characters"))
            ))
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

    private fun isStringSource(contents: String, index: Int): Matcher<Source> {
        return cast(allOf(
            has(StringSource::contents, equalTo(contents)),
            has(StringSource::characterIndex, equalTo(index))
        ))
    }
}
