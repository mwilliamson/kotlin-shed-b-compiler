package org.shedlang.compiler.tests.parser

import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.parser.*


internal fun <T> parseString(parser: (Source, TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = tokeniseWithoutWhitespace(input)
    return parser.parse(tokens)
}

internal fun <T> parseString(parser: (TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = tokeniseWithoutWhitespace(input)
    return parser(tokens)
}

private fun tokeniseWithoutWhitespace(input: String): TokenIterator<TokenType> {
    val tokens = tokenise(input)
        .filter({ token -> token.tokenType != TokenType.WHITESPACE })
    val end = Token(input.length, TokenType.END, "")
    return TokenIterator("<string>", tokens, end)
}
