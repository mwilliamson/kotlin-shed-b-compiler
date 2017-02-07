package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.*

internal fun parse(filename: String, input: String): ModuleNode {
    val tokens = tokenise(input)
        .filter { token -> token.tokenType != TokenType.WHITESPACE }
    val tokenIterator = TokenIterator(
        filename,
        tokens,
        end = Token(input.length, TokenType.END, "")
    )
    val module = ::parseModule.parse(tokenIterator)
    tokenIterator.skip(TokenType.END)
    return module
}

internal fun <T> ((Source, TokenIterator<TokenType>) -> T).parse(tokens: TokenIterator<TokenType>): T {
    val source = tokens.location()
    return this(source, tokens)
}

internal fun parseModule(source: Source, tokens: TokenIterator<TokenType>): ModuleNode {
    val moduleName = parseModuleNameDeclaration(tokens)
    val body = parseZeroOrMore(
        parseElement = ::parseModuleStatement,
        isEnd = { tokens -> tokens.isNext(TokenType.END) },
        tokens = tokens
    )
    return ModuleNode(moduleName, body, source)
}

private fun parseModuleNameDeclaration(tokens: TokenIterator<TokenType>): String {
    tokens.skip(TokenType.KEYWORD, "module")
    val moduleName = parseModuleName(tokens)
    tokens.skip(TokenType.SYMBOL, ";")
    return moduleName
}

internal fun parseModuleName(tokens: TokenIterator<TokenType>): String {
    return parseOneOrMoreWithSeparator(
        { tokens -> parseIdentifier(tokens) },
        { tokens -> tokens.trySkip(TokenType.SYMBOL, ".") },
        tokens
    ).joinToString(".")
}

internal fun parseModuleStatement(tokens: TokenIterator<TokenType>): ModuleStatementNode {
    if (tokens.isNext(TokenType.KEYWORD, "shape")) {
        return ::parseShape.parse(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD, "fun")) {
        return ::parseFunction.parse(tokens)
    } else {
        throw UnexpectedTokenException(
            expected = "module statement",
            actual = tokens.peek().describe(),
            location = tokens.location()
        )
    }
}

internal fun parseShape(source: Source, tokens: TokenIterator<TokenType>): ShapeNode {
    tokens.skip(TokenType.KEYWORD, "shape")
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL, "{")

    val fields = parseZeroOrMoreNodes(
        parseElement = ::parseShapeField,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL, ",") },
        isEnd = { tokens.isNext(TokenType.SYMBOL, "}") },
        tokens = tokens,
        allowTrailingSeparator = true
    )

    tokens.skip(TokenType.SYMBOL, "}")
    return ShapeNode(
        name = name,
        fields = fields,
        source = source
    )
}

private fun parseShapeField(source: Source, tokens: TokenIterator<TokenType>): ShapeFieldNode {
    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return ShapeFieldNode(
        name = name,
        type = type,
        source = source
    )
}

internal fun parseFunction(source: Source, tokens: TokenIterator<TokenType>): FunctionNode {
    tokens.skip(TokenType.KEYWORD, "fun")

    val name = parseIdentifier(tokens)

    tokens.skip(TokenType.SYMBOL, "(")
    val arguments = parseZeroOrMoreNodes(
        parseElement = ::parseFormalArgument,
        parseSeparator = {tokens -> tokens.skip(TokenType.SYMBOL, ",")},
        isEnd = { tokens.isNext(TokenType.SYMBOL, ")") },
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL, ")")
    val returnType = parseTypeSpec(tokens)
    val body = parseFunctionStatements(tokens)

    return FunctionNode(
        name = name,
        arguments = arguments,
        returnType = returnType,
        body = body,
        source = source
    )
}

private fun parseFunctionStatements(tokens: TokenIterator<TokenType>): List<StatementNode> {
    tokens.skip(TokenType.SYMBOL, "{")
    val body = parseZeroOrMore(
        parseElement = ::parseFunctionStatement,
        isEnd = { tokens.isNext(TokenType.SYMBOL, "}") },
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL, "}")
    return body
}

private fun parseFormalArgument(source: Source, tokens: TokenIterator<TokenType>) : ArgumentNode {
    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return ArgumentNode(name, type, source)
}

private fun parseTypeSpec(tokens: TokenIterator<TokenType>): TypeNode {
    tokens.skip(TokenType.SYMBOL, ":")
    val type = ::parseType.parse(tokens)
    return type
}

internal fun parseFunctionStatement(tokens: TokenIterator<TokenType>) : StatementNode {
    val token = tokens.peek()
    if (token.tokenType == TokenType.KEYWORD) {
        val parseStatement : ((Source, TokenIterator<TokenType>) -> StatementNode)? = when (token.value) {
            "return" -> ::parseReturn
            "if" -> ::parseIfStatement
            "val" -> ::parseVal
            else -> null
        }
        if (parseStatement != null) {
            tokens.skip()
            return parseStatement.parse(tokens)
        }
    }
    val expressionStatement = ::tryParseExpressionStatement.parse(tokens)
    if (expressionStatement == null) {
        throw UnexpectedTokenException(
            location = tokens.location(),
            expected = "function statement",
            actual = token.describe()
        )
    } else {
        return expressionStatement
    }
}

private fun tryParseExpressionStatement(source: Source, tokens: TokenIterator<TokenType>) : ExpressionStatementNode? {
    val expression = tryParseExpression(tokens)
    if (expression == null) {
        return null
    } else {
        tokens.skip(TokenType.SYMBOL, ";")
        return ExpressionStatementNode(expression, source)
    }
}

private fun parseReturn(source: Source, tokens: TokenIterator<TokenType>) : ReturnNode {
    val expression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL, ";")
    return ReturnNode(expression, source)
}

private fun parseIfStatement(source: Source, tokens: TokenIterator<TokenType>) : IfStatementNode {
    tokens.skip(TokenType.SYMBOL, "(")
    val condition = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL, ")")

    val trueBranch = parseFunctionStatements(tokens)

    tokens.skip(TokenType.KEYWORD, "else")
    val falseBranch = parseFunctionStatements(tokens)

    return IfStatementNode(
        condition = condition,
        trueBranch = trueBranch,
        falseBranch = falseBranch,
        source = source
    )
}

private fun parseVal(source: Source, tokens: TokenIterator<TokenType>): ValNode {
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL, "=")
    val expression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL, ";")

    return ValNode(
        name = name,
        expression = expression,
        source = source
    )
}

internal fun parseExpression(tokens: TokenIterator<TokenType>) : ExpressionNode {
    return parseExpression(tokens, precedence = Int.MIN_VALUE)
}

private fun tryParseExpression(tokens: TokenIterator<TokenType>) : ExpressionNode? {
    return tryParseExpression(tokens, precedence = Int.MIN_VALUE)
}

private fun parseExpression(tokens: TokenIterator<TokenType>, precedence: Int) : ExpressionNode {
    val expression = tryParseExpression(tokens, precedence = precedence)
    if (expression == null) {
        throw UnexpectedTokenException(
            expected = "expression",
            actual = tokens.peek().describe(),
            location = tokens.location()
        )
    } else {
        return expression
    }
}

private fun tryParseExpression(tokens: TokenIterator<TokenType>, precedence: Int) : ExpressionNode? {
    val primaryExpression = ::tryParsePrimaryExpression.parse(tokens)
    if (primaryExpression == null) {
        return null
    }

    var left: ExpressionNode = primaryExpression
    while (true) {
        val next = tokens.peek()
        if (next.tokenType == TokenType.SYMBOL) {
            val operationParser = lookupOperator(next.value)
            if (operationParser == null || operationParser.precedence < precedence) {
                return left
            } else {
                tokens.skip()
                left = operationParser.parse(left, tokens)
            }
        } else {
            return left
        }
    }
}

private fun lookupOperator(operator: String) : OperationParser? {
    return when (operator) {
        "==" -> OperationParser.EQUALS
        "+" -> OperationParser.ADD
        "-" -> OperationParser.SUBTRACT
        "*" -> OperationParser.MULTIPLY
        "(" -> OperationParser.CALL
        "." -> OperationParser.FIELD_ACCESS
        else -> null
    }
}

private interface OperationParser {
    val precedence: Int

    fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode

    companion object {
        val EQUALS = InfixOperationParser(Operator.EQUALS, 8)
        val ADD = InfixOperationParser(Operator.ADD, 11)
        val SUBTRACT = InfixOperationParser(Operator.SUBTRACT, 11)
        val MULTIPLY = InfixOperationParser(Operator.MULTIPLY, 12)
        val CALL = FunctionCallParser
        val FIELD_ACCESS = FieldAccessParser
    }
}

private class InfixOperationParser(
    private val operator: Operator,
    override val precedence: Int
) : OperationParser {
    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val right = parseExpression(tokens, precedence + 1)
        return BinaryOperationNode(operator, left, right, left.source)
    }
}

private object FunctionCallParser : OperationParser {
    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val arguments = parseZeroOrMore(
            parseElement = { tokens -> parseArgument(tokens) },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL, ",") },
            isEnd = { tokens.isNext(TokenType.SYMBOL, ")") },
            tokens = tokens
        )
        // TODO: check named arguments appear after all positional arguments
        tokens.skip(TokenType.SYMBOL, ")")
        val positionalArguments = arguments
            .filterIsInstance<ParsedArgument.Positional>()
            .map({ parsedArgument -> parsedArgument.expression })
        val namedArguments = arguments
            .filterIsInstance<ParsedArgument.Named>()
            .associate({ parsedArgument -> parsedArgument.name to parsedArgument.expression })
        return FunctionCallNode(
            function = left,
            positionalArguments = positionalArguments,
            namedArguments = namedArguments,
            source = left.source
        )
    }

    private sealed class ParsedArgument {
        class Positional(val expression: ExpressionNode): ParsedArgument()
        class Named(val name: String, val expression: ExpressionNode): ParsedArgument()
    }

    private fun parseArgument(tokens: TokenIterator<TokenType>): ParsedArgument {
        if (tokens.isNext(TokenType.IDENTIFIER) && tokens.isNext(TokenType.SYMBOL, "=", skip = 1)) {
            val name = parseIdentifier(tokens)
            tokens.skip(TokenType.SYMBOL, "=")
            val expression = parseExpression(tokens)
            return ParsedArgument.Named(name, expression)
        } else {
            val expression = parseExpression(tokens)
            return ParsedArgument.Positional(expression)
        }
    }

    override val precedence: Int
        get() = 14
}

private object FieldAccessParser : OperationParser {
    override val precedence: Int
        get() = 14

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val fieldName = parseIdentifier(tokens)
        return FieldAccessNode(
            receiver = left,
            fieldName = fieldName,
            source = left.source
        )
    }

}

internal fun tryParsePrimaryExpression(source: Source, tokens: TokenIterator<TokenType>) : ExpressionNode? {
    val token = tokens.next();
    when (token.tokenType) {
        TokenType.INTEGER -> return IntegerLiteralNode(token.value.toInt(), source)
        TokenType.IDENTIFIER -> return VariableReferenceNode(token.value, source)
        TokenType.KEYWORD -> when (token.value) {
            "true" -> return BooleanLiteralNode(true, source)
            "false" -> return BooleanLiteralNode(false, source)
        }
        TokenType.SYMBOL -> when (token.value) {
            "(" -> {
                val expression = parseExpression(tokens)
                tokens.skip(TokenType.SYMBOL, ")")
                return expression
            }
        }
    }
    return null
}

internal fun parseType(source: Source, tokens: TokenIterator<TokenType>) : TypeNode {
    val name = parseIdentifier(tokens)
    return TypeReferenceNode(name, source)
}

private fun parseIdentifier(tokens: TokenIterator<TokenType>) = tokens.nextValue(TokenType.IDENTIFIER)

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
    parseElement: (Source, TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Unit,
    isEnd: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>,
    allowTrailingSeparator: Boolean = false
) : List<T> {
    return parseZeroOrMore(
        { tokens -> parseElement.parse(tokens) },
        parseSeparator,
        isEnd,
        tokens,
        allowTrailingSeparator = allowTrailingSeparator
    )
}

private fun <T> parseZeroOrMore(
    parseElement: (TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Unit = { tokens -> },
    isEnd: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>,
    allowTrailingSeparator: Boolean = false
) : List<T> {
    if (isEnd(tokens)) {
        return listOf()
    }

    val elements = mutableListOf<T>()

    while (true) {
        elements.add(parseElement(tokens))
        if (isEnd(tokens)) {
            return elements
        }
        parseSeparator(tokens)
        if (allowTrailingSeparator && isEnd(tokens)) {
            return elements
        }
    }
}
