package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.StringSource
import org.shedlang.compiler.parser.Token
import org.shedlang.compiler.parser.TokenIterator
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.tests.allOf

class TokenIteratorTests {
    enum class TokenType {
        IDENTIFIER,
        SYMBOL,
        END
    }

    @Nested
    inner class SkipByTokenType {
        @Test
        fun movesToNextTokenWhenTokenTypeMatches() {
            val tokens = tokenIterator(listOf(
                Token(0, TokenType.IDENTIFIER, "a"),
                Token(1, TokenType.IDENTIFIER, "b")
            ))
            tokens.skip(TokenType.IDENTIFIER)
            assertThat(tokens.location(), has(StringSource::characterIndex, equalTo(1)))
        }

        @Test
        fun throwsExceptionWhenNextTokenHasUnexpectedType() {
            val tokens = tokenIterator(listOf(
                Token(0, TokenType.IDENTIFIER, "a")
            ))
            val exception = assertThrows<UnexpectedTokenException>(
                UnexpectedTokenException::class.java,
                { tokens.skip(TokenType.SYMBOL) }
            )
            assertThat(exception, allOf(
                has(UnexpectedTokenException::location, equalTo(StringSource("<string>", 0))),
                has(UnexpectedTokenException::expected, equalTo("token of type SYMBOL")),
                has(UnexpectedTokenException::actual, equalTo("IDENTIFIER: a"))
            ))
        }
    }

    @Nested
    inner class SkipByTokenTypeAndValue {
        @Test
        fun movesToNextTokenWhenTokenTypeAndValueMatch() {
            val tokens = tokenIterator(listOf(
                Token(0, TokenType.IDENTIFIER, "a"),
                Token(1, TokenType.IDENTIFIER, "b")
            ))
            tokens.skip(TokenType.IDENTIFIER, "a")
            assertThat(tokens.location(), has(StringSource::characterIndex, equalTo(1)))
        }

        @Test
        fun throwsExceptionWhenNextTokenHasUnexpectedType() {
            val tokens = tokenIterator(listOf(
                Token(0, TokenType.IDENTIFIER, "a")
            ))
            val exception = assertThrows<UnexpectedTokenException>(
                UnexpectedTokenException::class.java,
                { tokens.skip(TokenType.SYMBOL, "a") }
            )
            assertThat(exception, allOf(
                has(UnexpectedTokenException::location, equalTo(StringSource("<string>", 0))),
                has(UnexpectedTokenException::expected, equalTo("SYMBOL: a")),
                has(UnexpectedTokenException::actual, equalTo("IDENTIFIER: a"))
            ))
        }

        @Test
        fun throwsExceptionWhenNextTokenHasUnexpectedValue() {
            val tokens = tokenIterator(listOf(
                Token(0, TokenType.IDENTIFIER, "a")
            ))
            val exception = assertThrows<UnexpectedTokenException>(
                UnexpectedTokenException::class.java,
                { tokens.skip(TokenType.IDENTIFIER, "b") }
            )
            assertThat(exception, allOf(
                has(UnexpectedTokenException::location, equalTo(StringSource("<string>", 0))),
                has(UnexpectedTokenException::expected, equalTo("IDENTIFIER: b")),
                has(UnexpectedTokenException::actual, equalTo("IDENTIFIER: a"))
            ))
        }
    }

    @Test
    fun whenThereAreNoMoreTokensThenPeekReturnsEndToken() {
        val tokens = tokenIterator(listOf())
        assertThat(tokens.peek(), equalTo(END_TOKEN))
    }

    private val END_TOKEN = Token(-1, TokenType.END, "")

    private fun tokenIterator(tokens: List<Token<TokenType>>): TokenIterator<TokenType> {
        return TokenIterator("<string>", tokens, end = END_TOKEN)
    }
}
