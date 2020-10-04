package org.shedlang.compiler.parser;


private fun keyword(tokenType: TokenType, string: String)
    = RegexTokeniser.rule(tokenType, Regex.escape(string) + "(?![A-Za-z0-9])")


private fun symbol(tokenType: TokenType, string: String)
    = RegexTokeniser.rule(tokenType, Regex.escape(string))

private val unterminatedStringPattern = "\"(?:[^\\\\\"\n\r]|\\\\.)*"

val identifierPattern = "[A-Za-z][A-Za-z0-9]*"

private val tokeniser = RegexTokeniser(TokenType.UNKNOWN, listOf(
    keyword(TokenType.KEYWORD_AS, "as"),
    keyword(TokenType.KEYWORD_EFFECT, "effect"),
    keyword(TokenType.KEYWORD_ELSE, "else"),
    keyword(TokenType.KEYWORD_EXIT, "exit"),
    keyword(TokenType.KEYWORD_EXPORT, "export"),
    keyword(TokenType.KEYWORD_EXTENDS, "extends"),
    keyword(TokenType.KEYWORD_FALSE, "false"),
    keyword(TokenType.KEYWORD_FROM, "from"),
    keyword(TokenType.KEYWORD_FUN, "fun"),
    keyword(TokenType.KEYWORD_FUN_CAPITAL, "Fun"),
    keyword(TokenType.KEYWORD_HANDLE, "handle"),
    keyword(TokenType.KEYWORD_IF, "if"),
    keyword(TokenType.KEYWORD_IMPORT, "import"),
    keyword(TokenType.KEYWORD_IS, "is"),
    keyword(TokenType.KEYWORD_MODULE, "module"),
    keyword(TokenType.KEYWORD_NOT, "not"),
    keyword(TokenType.KEYWORD_ON, "on"),
    keyword(TokenType.KEYWORD_PARTIAL, "partial"),
    keyword(TokenType.KEYWORD_RESUME, "resume"),
    keyword(TokenType.KEYWORD_RETURN, "return"),
    keyword(TokenType.KEYWORD_SHAPE, "shape"),
    keyword(TokenType.KEYWORD_TAILREC, "tailrec"),
    keyword(TokenType.KEYWORD_TRUE, "true"),
    keyword(TokenType.KEYWORD_TYPE, "type"),
    keyword(TokenType.KEYWORD_UNION, "union"),
    keyword(TokenType.KEYWORD_UNIT, "unit"),
    keyword(TokenType.KEYWORD_VAL, "val"),
    keyword(TokenType.KEYWORD_VARARGS, "varargs"),
    keyword(TokenType.KEYWORD_WHEN, "when"),
    keyword(TokenType.KEYWORD_WITH_STATE, "withstate"),

    RegexTokeniser.rule(TokenType.INTEGER, "-?[0-9]+"),

    symbol(TokenType.SYMBOL_ARROW, "->"),
    symbol(TokenType.SYMBOL_FAT_ARROW, "=>"),
    symbol(TokenType.SYMBOL_SUBTYPE, "<:"),
    symbol(TokenType.SYMBOL_PIPELINE, "|>"),
    symbol(TokenType.SYMBOL_DOUBLE_EQUALS, "=="),
    symbol(TokenType.SYMBOL_NOT_EQUAL, "!="),
    symbol(TokenType.SYMBOL_LESS_THAN_OR_EQUAL, "<="),
    symbol(TokenType.SYMBOL_GREATER_THAN_OR_EQUAL, ">="),
    symbol(TokenType.SYMBOL_DOUBLE_AMPERSAND, "&&"),
    symbol(TokenType.SYMBOL_DOUBLE_VERTICAL_BAR, "||"),

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
    symbol(TokenType.SYMBOL_HASH, "#"),
    symbol(TokenType.SYMBOL_UNDERSCORE, "_"),

    RegexTokeniser.rule(TokenType.IDENTIFIER, identifierPattern),
    RegexTokeniser.rule(TokenType.STRING, unterminatedStringPattern + "\""),
    RegexTokeniser.rule(TokenType.UNTERMINATED_STRING, unterminatedStringPattern),
    RegexTokeniser.rule(TokenType.CODE_POINT, "'(?:[^\\\\'\n\r]|\\\\.)*'"),
    RegexTokeniser.rule(TokenType.WHITESPACE, "[\r\n\t ]+"),
    RegexTokeniser.rule(TokenType.COMMENT, "//[^\n]*"),

    symbol(TokenType.SYMBOL_AT, "@")
))

internal fun tokenise(value: String): List<Token<TokenType>> {
    return tokeniser.tokenise(value)
}

internal enum class TokenType {
    UNKNOWN,

    KEYWORD_AS,
    KEYWORD_EFFECT,
    KEYWORD_ELSE,
    KEYWORD_EXIT,
    KEYWORD_EXPORT,
    KEYWORD_EXTENDS,
    KEYWORD_FALSE,
    KEYWORD_FROM,
    KEYWORD_FUN,
    KEYWORD_FUN_CAPITAL,
    KEYWORD_HANDLE,
    KEYWORD_IF,
    KEYWORD_IMPORT,
    KEYWORD_IS,
    KEYWORD_MODULE,
    KEYWORD_NOT,
    KEYWORD_ON,
    KEYWORD_PARTIAL,
    KEYWORD_RESUME,
    KEYWORD_RETURN,
    KEYWORD_SHAPE,
    KEYWORD_TAILREC,
    KEYWORD_TRUE,
    KEYWORD_TYPE,
    KEYWORD_UNION,
    KEYWORD_UNIT,
    KEYWORD_VAL,
    KEYWORD_VARARGS,
    KEYWORD_WHEN,
    KEYWORD_WITH_STATE,

    IDENTIFIER,

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
    SYMBOL_NOT_EQUAL,
    SYMBOL_LESS_THAN,
    SYMBOL_LESS_THAN_OR_EQUAL,
    SYMBOL_GREATER_THAN,
    SYMBOL_GREATER_THAN_OR_EQUAL,
    SYMBOL_DOUBLE_AMPERSAND,
    SYMBOL_DOUBLE_VERTICAL_BAR,
    SYMBOL_PLUS,
    SYMBOL_MINUS,
    SYMBOL_ASTERISK,
    SYMBOL_BAR,
    SYMBOL_TILDE,
    SYMBOL_BANG,
    SYMBOL_HASH,
    SYMBOL_AT,
    SYMBOL_UNDERSCORE,

    INTEGER,
    STRING,
    UNTERMINATED_STRING,
    CODE_POINT,
    WHITESPACE,
    COMMENT,
    END
}
