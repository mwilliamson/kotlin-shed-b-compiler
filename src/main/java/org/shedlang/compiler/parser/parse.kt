package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.ModuleNode

internal fun parseModule(input: String): ModuleNode {
    val tokens = tokenise(input)
        .filter { token -> token.tokenType != TokenType.WHITESPACE }
    return parseModule(TokenIterator(tokens))
}

internal fun parseModule(tokens: TokenIterator<TokenType>): ModuleNode {
    tokens.skip(TokenType.KEYWORD, "module")
    val moduleName = parseModuleName(tokens)
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
