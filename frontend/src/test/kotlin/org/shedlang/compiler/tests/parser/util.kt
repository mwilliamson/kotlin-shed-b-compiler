package org.shedlang.compiler.tests.parser

import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.parser.*


internal fun <T> parseString(parser: (Source, TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = TokenIterator("<string>", tokeniseWithoutWhitespace(input))
    return parser.parse(tokens)
}

internal fun <T> parseString(parser: (TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = TokenIterator("<string>", tokeniseWithoutWhitespace(input))
    return parser(tokens)
}

private fun tokeniseWithoutWhitespace(input: String): List<Token<TokenType>> {
    return tokenise(input)
        .filter({ token -> token.tokenType != TokenType.WHITESPACE })
        .plus(Token(input.length, TokenType.END, ""))
}
