package org.shedlang.compiler.parser;


private fun literal(tokenType: TokenType, string: String)
    = RegexTokeniser.rule(tokenType, Regex.escape(string))

private val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
    literal(TokenType.KEYWORD_ELSE, "else"),
    literal(TokenType.KEYWORD_FALSE, "false"),
    literal(TokenType.KEYWORD_FUN, "fun"),
    literal(TokenType.KEYWORD_IF, "if"),
    literal(TokenType.KEYWORD_MODULE, "module"),
    literal(TokenType.KEYWORD_RETURN, "return"),
    literal(TokenType.KEYWORD_SHAPE, "shape"),
    literal(TokenType.KEYWORD_TRUE, "true"),
    literal(TokenType.KEYWORD_UNION, "union"),
    literal(TokenType.KEYWORD_VAL, "val"),

    RegexTokeniser.rule(TokenType.INTEGER, "-?[0-9]+"),

    literal(TokenType.SYMBOL_DOT, "."),
    literal(TokenType.SYMBOL_COMMA, ","),
    literal(TokenType.SYMBOL_COLON, ":"),
    literal(TokenType.SYMBOL_SEMICOLON, ";"),
    literal(TokenType.SYMBOL_OPEN_PAREN, "("),
    literal(TokenType.SYMBOL_CLOSE_PAREN, ")"),
    literal(TokenType.SYMBOL_OPEN_BRACE, "{"),
    literal(TokenType.SYMBOL_CLOSE_BRACE, "}"),
    literal(TokenType.SYMBOL_DOUBLE_EQUALS, "=="),
    literal(TokenType.SYMBOL_EQUALS, "="),
    literal(TokenType.SYMBOL_PLUS, "+"),
    literal(TokenType.SYMBOL_MINUS, "-"),
    literal(TokenType.SYMBOL_ASTERISK, "*"),

    RegexTokeniser.rule(TokenType.IDENTIFIER, "[A-Za-z][A-Za-z0-9]*"),
    RegexTokeniser.rule(TokenType.WHITESPACE, "[\r\n\t ]+")
))

internal fun tokenise(value: String): List<Token<TokenType>> {
    return tokeniser.tokenise(value)
}

internal enum class TokenType {
    UNKNOWN,

    KEYWORD_ELSE,
    KEYWORD_FALSE,
    KEYWORD_FUN,
    KEYWORD_IF,
    KEYWORD_MODULE,
    KEYWORD_RETURN,
    KEYWORD_SHAPE,
    KEYWORD_TRUE,
    KEYWORD_UNION,
    KEYWORD_VAL,

    IDENTIFIER,

    SYMBOL_DOT,
    SYMBOL_COMMA,
    SYMBOL_COLON,
    SYMBOL_SEMICOLON,
    SYMBOL_OPEN_PAREN,
    SYMBOL_CLOSE_PAREN,
    SYMBOL_OPEN_BRACE,
    SYMBOL_CLOSE_BRACE,
    SYMBOL_DOUBLE_EQUALS,
    SYMBOL_EQUALS,
    SYMBOL_PLUS,
    SYMBOL_MINUS,
    SYMBOL_ASTERISK,

    INTEGER,
    WHITESPACE,
    END
}
