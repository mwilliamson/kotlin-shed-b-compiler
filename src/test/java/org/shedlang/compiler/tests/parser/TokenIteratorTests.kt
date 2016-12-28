package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.SourceLocation
import org.shedlang.compiler.parser.Token
import org.shedlang.compiler.parser.TokenIterator
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.tests.allOf

class TokenIteratorTests {
    enum class TokenType {
        IDENTIFIER,
        SYMBOL
    }

    @Test
    fun skipMovesToNextTokenWhenTokenTypeMatches() {
        val tokens = TokenIterator("<string>", listOf(
            Token(0, TokenType.IDENTIFIER, "a"),
            Token(1, TokenType.IDENTIFIER, "b")
        ))
        tokens.skip(TokenType.IDENTIFIER)
        assertThat(tokens.location(), has(SourceLocation::characterIndex, equalTo(1)))
    }

    @Test
    fun skipThrowsExceptionWhenNextTokenHasUnexpectedType() {
        val tokens = TokenIterator("<string>", listOf(
            Token(0, TokenType.IDENTIFIER, "a")
        ))
        val exception = assertThrows<UnexpectedTokenException>(
            UnexpectedTokenException::class.java,
            { tokens.skip(TokenType.SYMBOL) }
        )
        assertThat(exception, allOf(
            has(UnexpectedTokenException::location, equalTo(SourceLocation("<string>", 0))),
            has(UnexpectedTokenException::expected, equalTo("token of type SYMBOL")),
            has(UnexpectedTokenException::actual, equalTo("IDENTIFIER: a"))
        ))
    }
}