package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.ModuleNode

internal fun parse(input: String): ModuleNode {
    val tokens = tokenise(input)
        .filter { token -> token.tokenType != TokenType.WHITESPACE }
        .plus(Token(input.length, TokenType.END, ""))
    val tokenIterator = TokenIterator(tokens)
    val module = parseModule(tokenIterator)
    tokenIterator.skip(TokenType.END)
    return module
}

internal fun parseModule(tokens: TokenIterator<TokenType>): ModuleNode {
    tokens.skip(TokenType.KEYWORD, "module")
    val moduleName = parseModuleName(tokens)
    tokens.skip(TokenType.SYMBOL, ";")
    return ModuleNode(moduleName)
}

internal fun parseModuleName(tokens: TokenIterator<TokenType>): String {
    return parseWithSeparator(
        { tokens -> tokens.nextValue(TokenType.IDENTIFIER) },
        { tokens -> tokens.trySkip(TokenType.SYMBOL, ".") },
        tokens
    ).joinToString(".")
}

private fun <T> parseWithSeparator(
    parseElement: (TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>
): List<T> {
    val elements = mutableListOf(parseElement(tokens))
    while (parseSeparator(tokens)) {
        elements.add(parseElement(tokens))
    }
    return elements
}
