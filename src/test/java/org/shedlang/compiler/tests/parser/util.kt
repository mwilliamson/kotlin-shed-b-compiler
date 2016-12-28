package org.shedlang.compiler.tests.parser

import org.shedlang.compiler.ast.SourceLocation
import org.shedlang.compiler.parser.TokenIterator
import org.shedlang.compiler.parser.TokenType
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.parser.tokenise


internal fun <T> parseString(parser: (SourceLocation, TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = TokenIterator("<string>", tokenise(input))
    return parser.parse(tokens)
}

internal fun <T> parseString(parser: (TokenIterator<TokenType>) -> T, input: String): T {
    val tokens = TokenIterator("<string>", tokenise(input))
    return parser(tokens)
}