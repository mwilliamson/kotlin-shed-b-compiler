package org.shedlang.compiler.parser

import org.shedlang.compiler.orElseThrow

internal class TokenIterator<T>(private val tokens: List<Token<T>>) {
    var index = 0

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

    fun skip(tokenType: T, value: String) {
        val token = peek()
        if (token.tokenType == tokenType && token.value == value) {
            index++
        } else {
            throw RuntimeException("TODO")
        }
    }

    fun nextValue(tokenType: T): String {
        val token = peek()
        if (token.tokenType == tokenType) {
            index++
            return token.value
        } else {
            throw RuntimeException("TODO")
        }
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
}
