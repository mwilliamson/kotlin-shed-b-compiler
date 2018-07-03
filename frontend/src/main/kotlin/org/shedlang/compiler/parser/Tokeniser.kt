package org.shedlang.compiler.parser;


private fun keyword(tokenType: TokenType, string: String)
    = RegexTokeniser.rule(tokenType, Regex.escape(string) + "(?![A-Za-z0-9])")


private fun symbol(tokenType: TokenType, string: String)
    = RegexTokeniser.rule(tokenType, Regex.escape(string))

private val unterminatedStringPattern = "\"(?:[^\\\\\"\n\r]|\\\\.)*"

val identifierPattern = "[A-Za-z][A-Za-z0-9]*"

private val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
    keyword(TokenType.KEYWORD_ELSE, "else"),
    keyword(TokenType.KEYWORD_EXTENDS, "extends"),
    keyword(TokenType.KEYWORD_FALSE, "false"),
    keyword(TokenType.KEYWORD_FUN, "fun"),
    keyword(TokenType.KEYWORD_IF, "if"),
    keyword(TokenType.KEYWORD_IMPORT, "import"),
    keyword(TokenType.KEYWORD_IS, "is"),
    keyword(TokenType.KEYWORD_MODULE, "module"),
    keyword(TokenType.KEYWORD_RETURN, "return"),
    keyword(TokenType.KEYWORD_SHAPE, "shape"),
    keyword(TokenType.KEYWORD_TRUE, "true"),
    keyword(TokenType.KEYWORD_UNION, "union"),
    keyword(TokenType.KEYWORD_UNIT, "unit"),
    keyword(TokenType.KEYWORD_VAL, "val"),
    keyword(TokenType.KEYWORD_WHEN, "when"),

    RegexTokeniser.rule(TokenType.INTEGER, "-?[0-9]+"),

    symbol(TokenType.SYMBOL_ARROW, "->"),
    symbol(TokenType.SYMBOL_FAT_ARROW, "=>"),
    symbol(TokenType.SYMBOL_SUBTYPE, "<:"),
    symbol(TokenType.SYMBOL_PIPELINE, "|>"),
    symbol(TokenType.SYMBOL_DOUBLE_EQUALS, "=="),
    symbol(TokenType.SYMBOL_LESS_THAN_OR_EQUAL, "<="),
    symbol(TokenType.SYMBOL_GREATER_THAN_OR_EQUAL, ">="),

    symbol(TokenType.SYMBOL_DOT, "."),
    symbol(TokenType.SYMBOL_COMMA, ","),
    symbol(TokenType.SYMBOL_COLON, ":"),
    symbol(TokenType.SYMBOL_SEMICOLON, ";"),
    symbol(TokenType.SYMBOL_OPEN_PAREN, "("),
    symbol(TokenType.SYMBOL_CLOSE_PAREN, ")"),
    symbol(TokenType.SYMBOL_OPEN_BRACE, "{"),
    symbol(TokenType.SYMBOL_CLOSE_BRACE, "}"),
    symbol(TokenType.SYMBOL_OPEN_SQUARE_BRACKET, "["),
    symbol(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET, "]"),
    symbol(TokenType.SYMBOL_EQUALS, "="),
    symbol(TokenType.SYMBOL_LESS_THAN, "<"),
    symbol(TokenType.SYMBOL_GREATER_THAN, ">"),
    symbol(TokenType.SYMBOL_PLUS, "+"),
    symbol(TokenType.SYMBOL_MINUS, "-"),
    symbol(TokenType.SYMBOL_ASTERISK, "*"),
    symbol(TokenType.SYMBOL_BAR, "|"),
    symbol(TokenType.SYMBOL_TILDE, "~"),
    symbol(TokenType.SYMBOL_BANG, "!"),

    RegexTokeniser.rule(TokenType.IDENTIFIER, identifierPattern),
    RegexTokeniser.rule(TokenType.SYMBOL_NAME, "@" + identifierPattern),
    RegexTokeniser.rule(TokenType.STRING, unterminatedStringPattern + "\""),
    RegexTokeniser.rule(TokenType.UNTERMINATED_STRING, unterminatedStringPattern),
    RegexTokeniser.rule(TokenType.CHARACTER, "'(?:[^\\\\'\n\r]|\\\\.)*'"),
    RegexTokeniser.rule(TokenType.WHITESPACE, "[\r\n\t ]+"),
    RegexTokeniser.rule(TokenType.COMMENT, "//[^\n]*")
))

internal fun tokenise(value: String): List<Token<TokenType>> {
    return tokeniser.tokenise(value)
}

internal enum class TokenType {
    UNKNOWN,

    KEYWORD_ELSE,
    KEYWORD_EXTENDS,
    KEYWORD_FALSE,
    KEYWORD_FUN,
    KEYWORD_IF,
    KEYWORD_IMPORT,
    KEYWORD_IS,
    KEYWORD_MODULE,
    KEYWORD_RETURN,
    KEYWORD_SHAPE,
    KEYWORD_TRUE,
    KEYWORD_UNION,
    KEYWORD_UNIT,
    KEYWORD_VAL,
    KEYWORD_WHEN,

    IDENTIFIER,
    SYMBOL_NAME,

    SYMBOL_ARROW,
    SYMBOL_FAT_ARROW,
    SYMBOL_SUBTYPE,
    SYMBOL_PIPELINE,
    SYMBOL_DOT,
    SYMBOL_COMMA,
    SYMBOL_COLON,
    SYMBOL_SEMICOLON,
    SYMBOL_OPEN_PAREN,
    SYMBOL_CLOSE_PAREN,
    SYMBOL_OPEN_BRACE,
    SYMBOL_CLOSE_BRACE,
    SYMBOL_OPEN_SQUARE_BRACKET,
    SYMBOL_CLOSE_SQUARE_BRACKET,
    SYMBOL_DOUBLE_EQUALS,
    SYMBOL_EQUALS,
    SYMBOL_LESS_THAN,
    SYMBOL_LESS_THAN_OR_EQUAL,
    SYMBOL_GREATER_THAN,
    SYMBOL_GREATER_THAN_OR_EQUAL,
    SYMBOL_PLUS,
    SYMBOL_MINUS,
    SYMBOL_ASTERISK,
    SYMBOL_BAR,
    SYMBOL_TILDE,
    SYMBOL_BANG,

    INTEGER,
    STRING,
    UNTERMINATED_STRING,
    CHARACTER,
    WHITESPACE,
    COMMENT,
    END
}
