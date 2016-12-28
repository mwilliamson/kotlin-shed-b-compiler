package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.*

internal fun parse(filename: String, input: String): ModuleNode {
    val tokens = tokenise(input)
        .filter { token -> token.tokenType != TokenType.WHITESPACE }
        .plus(Token(input.length, TokenType.END, ""))
    val tokenIterator = TokenIterator(filename, tokens)
    val module = ::parseModule.parse(tokenIterator)
    tokenIterator.skip(TokenType.END)
    return module
}

internal fun <T> ((SourceLocation, TokenIterator<TokenType>) -> T).parse(tokens: TokenIterator<TokenType>): T {
    val location = tokens.location()
    return this(location, tokens)
}

internal fun parseModule(location: SourceLocation, tokens: TokenIterator<TokenType>): ModuleNode {
    val moduleName = parseModuleNameDeclaration(tokens)
    val body = parseManyNodes(
        ::tryParseFunction,
        tokens
    )
    return ModuleNode(moduleName, body, location)
}

private fun parseModuleNameDeclaration(tokens: TokenIterator<TokenType>): String {
    tokens.skip(TokenType.KEYWORD, "module")
    val moduleName = parseModuleName(tokens)
    tokens.skip(TokenType.SYMBOL, ";")
    return moduleName
}

internal fun parseModuleName(tokens: TokenIterator<TokenType>): String {
    return parseOneOrMoreWithSeparator(
        { tokens -> tokens.nextValue(TokenType.IDENTIFIER) },
        { tokens -> tokens.trySkip(TokenType.SYMBOL, ".") },
        tokens
    ).joinToString(".")
}

internal fun tryParseFunction(location: SourceLocation, tokens: TokenIterator<TokenType>): FunctionNode? {
    if (!tokens.trySkip(TokenType.KEYWORD, "fun")) {
        return null
    }

    val name = tokens.nextValue(TokenType.IDENTIFIER)

    tokens.skip(TokenType.SYMBOL, "(")
    val arguments = parseZeroOrMoreNodes(
        parseElement = ::parseFormalArgument,
        parseSeparator = {tokens -> tokens.skip(TokenType.SYMBOL, ",")},
        isEnd = { tokens.isNext(TokenType.SYMBOL, ")") },
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL, ")")
    tokens.skip(TokenType.SYMBOL, ":")
    val returnType = ::parseType.parse(tokens)
    tokens.skip(TokenType.SYMBOL, "{")
    tokens.skip(TokenType.SYMBOL, "}")

    return FunctionNode(name, arguments, returnType, location)
}

private fun parseFormalArgument(location: SourceLocation, tokens: TokenIterator<TokenType>) : ArgumentNode {
    val name = tokens.nextValue(TokenType.IDENTIFIER)
    tokens.skip(TokenType.SYMBOL, ":")
    val type = ::parseType.parse(tokens)
    return ArgumentNode(name, type, location)
}

internal fun parseType(location: SourceLocation, tokens: TokenIterator<TokenType>) : TypeNode {
    val name = tokens.nextValue(TokenType.IDENTIFIER)
    return TypeReferenceNode(name, location)
}

private fun <T> parseManyNodes(
    parseElement: (SourceLocation, TokenIterator<TokenType>) -> T?,
    tokens: TokenIterator<TokenType>
) : List<T> {
    return parseMany(
        { tokens -> parseElement.parse(tokens) },
        tokens
    )
}

private fun <T> parseMany(
    parseElement: (TokenIterator<TokenType>) -> T?,
    tokens: TokenIterator<TokenType>
): List<T> {
    val elements: MutableList<T> = mutableListOf()
    while (true) {
        val element = parseElement(tokens)
        if (element == null) {
            return elements
        } else {
            elements.add(element)
        }
    }
}

private fun <T> parseOneOrMoreNodesWithSeparator(
    parseElement: (SourceLocation, TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>
) : List<T> {
    return parseOneOrMoreWithSeparator(
        {tokens -> parseElement.parse(tokens)},
        parseSeparator,
        tokens
    )
}

private fun <T> parseOneOrMoreWithSeparator(
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

private fun <T> parseZeroOrMoreNodes(
    parseElement: (SourceLocation, TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Unit,
    isEnd: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>
) : List<T> {
    return parseZeroOrMore(
        { tokens -> parseElement.parse(tokens) },
        parseSeparator,
        isEnd,
        tokens
    )
}

private fun <T> parseZeroOrMore(
    parseElement: (TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Unit,
    isEnd: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>
) : List<T> {
    val elements = mutableListOf<T>()

    while (!isEnd(tokens)) {
        if (elements.isNotEmpty()) {
            parseSeparator(tokens)
        }
        elements.add(parseElement(tokens))
    }

    return elements
}