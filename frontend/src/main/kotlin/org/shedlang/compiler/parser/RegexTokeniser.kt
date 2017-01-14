package org.shedlang.compiler.parser

import java.nio.CharBuffer
import java.util.regex.Pattern

internal class RegexTokeniser<T>(unknown: T, rules: List<RegexTokeniser.TokenRule<T>>) {
    internal data class TokenRule<T>(val type: T, val regex: Pattern)

    private val rules: List<TokenRule<T>>

    init {
        this.rules = rules.plus(rule(unknown, "."))
    }

    internal fun tokenise(value: String): List<Token<T>> {
        val tokens : MutableList<Token<T>> = mutableListOf()
        val remaining = CharBuffer.wrap(value)
        while (remaining.hasRemaining()) {
            var matched = false
            for (rule in rules) {
                val matcher = rule.regex.matcher(remaining)
                if (matcher.lookingAt()) {
                    tokens.add(Token(remaining.position(), rule.type, matcher.group()))
                    remaining.position(remaining.position() + matcher.end())
                    matched = true
                    break
                }
            }
            if (!matched) {
                // Should be impossible
                throw RuntimeException("Remaining: " + remaining)
            }
        }
        return tokens
    }

    companion object {
        fun <T> rule(type: T, regex: String): TokenRule<T> {
            return TokenRule(type, Pattern.compile(regex))
        }
    }


}
