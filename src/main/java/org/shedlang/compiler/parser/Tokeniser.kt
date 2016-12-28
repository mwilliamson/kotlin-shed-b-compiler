package org.shedlang.compiler.parser;

private val keywords = setOf(
    "else",
    "fun",
    "if",
    "module"
)

private val symbols = setOf(
    ".",
    ",",
    ":",
    ";",
    "(",
    ")",
    "{",
    "}"
)

private fun literalChoice(choices: Iterable<String>): String {
    return choices.map { choice -> Regex.escape(choice) }.joinToString("|")
}

private val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
    RegexTokeniser.rule(TokenType.KEYWORD, literalChoice(keywords)),
    RegexTokeniser.rule(TokenType.SYMBOL, literalChoice(symbols)),
    RegexTokeniser.rule(TokenType.IDENTIFIER, "[A-Za-z][A-Za-z0-9]*"),
    RegexTokeniser.rule(TokenType.INTEGER, "-?[0-9]+"),
    RegexTokeniser.rule(TokenType.WHITESPACE, "[\r\n\t ]+")
))

internal fun tokenise(value: String): List<Token<TokenType>> {
    return tokeniser.tokenise(value)
}

internal enum class TokenType {
    UNKNOWN,
    KEYWORD,
    IDENTIFIER,
    SYMBOL,
    INTEGER,
    WHITESPACE,
    END
}
