package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.SourceLocation
import org.shedlang.compiler.orElseThrow

internal class UnexpectedTokenException(
    val location: SourceLocation,
    val expected: String,
    val actual: String
) : Exception("Error at $location\nExpected: $expected\nBut got: $actual")

internal class TokenIterator<T>(private val filename: String, private val tokens: List<Token<T>>) {
    private var index = 0

    fun location(): SourceLocation {
        return SourceLocation(filename, index)
    }

    fun trySkip(tokenType: T, value: String): Boolean {
        val token = tryPeek()
        if (token == null) {
            return false
        } else if (token.tokenType == tokenType && token.value == value) {
            index++
            return true
        } else {
            return false
        }
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

    private fun tryPeek(): Token<T>? {
        if (index < tokens.size) {
            return tokens[index]
        } else {
            return null
        }
    }

    private fun peek(): Token<T> {
        return tryPeek().orElseThrow(RuntimeException("TODO"))
    }

    private fun describeToken(tokenType: T, value: String) = "${tokenType}: ${value}"
}
