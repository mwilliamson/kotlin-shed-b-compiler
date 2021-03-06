package org.shedlang.compiler.tests.parser

import org.shedlang.compiler.ast.StringSource
import org.shedlang.compiler.parser.TokenIterator
import org.shedlang.compiler.parser.TokenType
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.parser.parserTokenise


internal fun <T> parseString(parser: (TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = tokeniseWithoutWhitespace(input)
    return parser(tokens)
}

private fun tokeniseWithoutWhitespace(input: String): TokenIterator<TokenType> {
    return parserTokenise(filename = "<filename>", input = input)
}
