package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.StringSource

internal class UnexpectedTokenException(
    val location: StringSource,
    val expected: String,
    val actual: String
) : Exception("Error at $location\nExpected: $expected\nBut got: $actual")

internal class TokenIterator<T>(
    private val filename: String,
    private val tokens: List<Token<T>>,
    private val end: Token<T>
) {
    private var index = 0

    fun location(): StringSource {
        return StringSource(filename, index)
    }

    fun trySkip(tokenType: T, value: String): Boolean {
        val isNext = isNext(tokenType, value)
        if (isNext) {
            index++
        }
        return isNext
    }

    fun skip() {
        peek()
        index++
    }

    fun skip(tokenType: T) {
        val token = peek()
        if (token.tokenType == tokenType) {
            index++
        } else {
            throw UnexpectedTokenException(
                location = location(),
                expected = "token of type " + tokenType,
                actual = describeToken(token.tokenType, token.value)
            )
        }
    }

    fun skip(tokenType: T, value: String) {
        val token = peek()
        if (token.tokenType == tokenType && token.value == value) {
            index++
        } else {
            throw UnexpectedTokenException(
                location = location(),
                expected = describeToken(tokenType, value),
                actual = describeToken(token.tokenType, token.value)
            )
        }
    }

    fun nextValue(tokenType: T): String {
        skip(tokenType)
        return tokens[index - 1].value
    }

    fun isNext(tokenType: T, value: String): Boolean {
        val token = peek()
        return token.tokenType == tokenType && token.value == value
    }

    fun peek(): Token<T> {
        return getToken(index)
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
