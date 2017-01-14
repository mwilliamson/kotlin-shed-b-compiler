package org.shedlang.compiler.tests.parser

import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.RegexTokeniser
import org.shedlang.compiler.parser.Token
import kotlin.test.assertEquals

class RegexTokeniserTests {
    enum class TokenType {
        UNKNOWN,
        KEYWORD,
        IDENTIFIER,
        WHITESPACE
    }

    @Test
    fun emptyStringIsTokenisedToNoTokens() {
        val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf())

        assertEquals(
            listOf(),
            tokeniser.tokenise("")
        )
    }

    @Test
    fun regexIsAppliedToGenerateToken() {
        val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
            RegexTokeniser.rule(TokenType.IDENTIFIER, "[A-Za-z0-9]+")
        ))

        assertEquals(
            listOf(Token(0, TokenType.IDENTIFIER, "count")),
            tokeniser.tokenise("count")
        )
    }

    @Test
    fun firstMatchingRegexIsUsed() {
        val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
            RegexTokeniser.rule(TokenType.KEYWORD, "if"),
            RegexTokeniser.rule(TokenType.IDENTIFIER, "[A-Za-z0-9]+")
        ))

        assertEquals(
            listOf(Token(0, TokenType.KEYWORD, "if")),
            tokeniser.tokenise("if")
        )
    }

    @Test
    fun sequenceOfTokensCanBeGenerated() {
        val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
            RegexTokeniser.rule(TokenType.IDENTIFIER, "[A-Za-z0-9]+"),
            RegexTokeniser.rule(TokenType.WHITESPACE, "\\s")
        ))

        assertEquals(
            listOf(
                Token(0, TokenType.IDENTIFIER, "one"),
                Token(3, TokenType.WHITESPACE, " "),
                Token(4, TokenType.IDENTIFIER, "two")
            ),
            tokeniser.tokenise("one two")
        )
    }

    @Test
    fun whenNoRulesMatchThenUnknownTokensAreGenerated() {
        val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
            RegexTokeniser.rule(TokenType.IDENTIFIER, "[A-Za-z0-9]+")
        ))

        assertEquals(
            listOf(
                Token(0, TokenType.UNKNOWN, "!"),
                Token(1, TokenType.UNKNOWN, "!"),
                Token(2, TokenType.IDENTIFIER, "a"),
                Token(3, TokenType.UNKNOWN, "!")
            ),
            tokeniser.tokenise("!!a!")
        )
    }
}
