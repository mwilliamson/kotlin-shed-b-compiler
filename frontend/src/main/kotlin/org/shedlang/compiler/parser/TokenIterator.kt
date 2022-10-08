package org.shedlang.compiler.parser

import org.shedlang.compiler.allIndexed
import org.shedlang.compiler.ast.StringSource

internal class UnexpectedTokenError(
    location: StringSource,
    val expected: String,
    val actual: String
) : ParseError(
    "Expected: $expected\nBut got: $actual",
    location
)

internal class TokenIterator<T>(
    private val locate: (Int) -> StringSource,
    private val tokens: List<Token<T>>,
    private val end: Token<T>
) {
    private var index = 0

    fun location(): StringSource {
        return locate(peek().characterIndex)
    }

    fun trySkip(tokenType: T): Boolean {
        val isNext = isNext(tokenType)
        if (isNext) {
            index++
        }
        return isNext
    }

    fun trySkip(tokenTypes: List<T>): Boolean {
        val isNext = tokenTypes.allIndexed { index, tokenType ->
            isNext(tokenType, skip = index)
        }
        if (isNext) {
            index += tokenTypes.size
        }
        return isNext
    }

    fun skip() {
        index++
    }

    fun skip(tokenType: T) {
        val token = peek()
        if (token.tokenType == tokenType) {
            index++
        } else {
            throw UnexpectedTokenError(
                location = location(),
                expected = "token of type " + tokenType,
                actual = describeToken(token.tokenType, token.value)
            )
        }
    }

    fun nextValue(tokenType: T): String {
        skip(tokenType)
        return tokens[index - 1].value
    }

    fun isNext(tokenType: T, skip: Int = 0): Boolean {
        val token = peek(skip = skip)
        return token.tokenType == tokenType
    }

    fun peek(skip: Int = 0): Token<T> {
        return getToken(index + skip)
    }

    fun next(): Token<T> {
        return getToken(index++)
    }

    private fun getToken(index: Int): Token<T> {
        if (index < tokens.size) {
            return tokens[index]
        } else {
            return end
        }
    }
}
