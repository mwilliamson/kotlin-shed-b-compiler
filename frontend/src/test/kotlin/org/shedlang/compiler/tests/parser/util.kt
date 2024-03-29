package org.shedlang.compiler.tests.parser

import org.shedlang.compiler.frontend.parser.TokenIterator
import org.shedlang.compiler.frontend.parser.TokenType
import org.shedlang.compiler.frontend.parser.parserTokenise


internal fun <T> parseString(parser: (TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = tokeniseWithoutWhitespace(input)
    val result = parser(tokens)
    tokens.skip(TokenType.END)
    return result
}

private fun tokeniseWithoutWhitespace(input: String): TokenIterator<TokenType> {
    return parserTokenise(filename = "<filename>", input = input)
}
