package org.shedlang.compiler.frontend.parser

import java.util.regex.Pattern

internal class RegexTokeniser<T>(unknown: T, rules: List<TokenRule<T>>) {
    internal data class TokenRule<T>(val type: T, val regex: Pattern) {
        init {
            if (regex.matcher("").groupCount() != 0) {
                throw RuntimeException("regex cannot contain any groups")
            }
        }
    }

    private val pattern: Pattern
    private val rules: List<T>

    init {
        val allRules = rules.plus(rule(unknown, "."))
        this.pattern = Pattern.compile(allRules.map({ rule -> "(${rule.regex.pattern()})" }).joinToString("|"))
        this.rules = allRules.map({ rule -> rule.type })
    }

    internal fun tokenise(value: String): List<Token<T>> {
        val matcher = pattern.matcher(value)
        val tokens : MutableList<Token<T>> = mutableListOf()
        while (matcher.lookingAt()) {
            val groupIndex = IntRange(1, this.rules.size).find({ index -> matcher.group(index) != null })!!
            val tokenType = this.rules[groupIndex - 1]
            tokens.add(Token(matcher.regionStart(), tokenType, matcher.group()))
            matcher.region(matcher.end(), value.length)
        }
        return tokens
    }

    companion object {
        fun <T> rule(type: T, regex: String): TokenRule<T> {
            return TokenRule(type, Pattern.compile(regex))
        }
    }


}
