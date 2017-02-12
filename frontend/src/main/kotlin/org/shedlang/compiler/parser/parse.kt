package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.*
import java.nio.CharBuffer
import java.util.regex.Pattern

internal class UnrecognisedEscapeSequenceError(
    val escapeSequence: String,
    val source: Source
) : Exception("Unrecognised escape sequence")

internal fun parse(filename: String, input: String): ModuleNode {
    val tokenIterator = parserTokenise(filename, input)
    val module = ::parseModule.parse(tokenIterator)
    tokenIterator.skip(TokenType.END)
    return module
}

internal fun parserTokenise(filename: String, input: String): TokenIterator<TokenType> {
    val tokens = tokenise(input)
        .filter { token -> token.tokenType != TokenType.WHITESPACE }
    return TokenIterator(
        locate = { characterIndex ->
            StringSource(
                filename = filename,
                contents = input,
                characterIndex = characterIndex
            )
        },
        tokens = tokens,
        end = Token(input.length, TokenType.END, "")
    )
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
    tokens.skip(TokenType.KEYWORD_MODULE)
    val moduleName = parseModuleName(tokens)
    tokens.skip(TokenType.SYMBOL_SEMICOLON)
    return moduleName
}

internal fun parseModuleName(tokens: TokenIterator<TokenType>): String {
    return parseOneOrMoreWithSeparator(
        { tokens -> parseIdentifier(tokens) },
        { tokens -> tokens.trySkip(TokenType.SYMBOL_DOT) },
        tokens
    ).joinToString(".")
}

internal fun parseModuleStatement(tokens: TokenIterator<TokenType>): ModuleStatementNode {
    if (tokens.isNext(TokenType.KEYWORD_SHAPE)) {
        return ::parseShape.parse(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_FUN)) {
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
    tokens.skip(TokenType.KEYWORD_SHAPE)
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)

    val fields = parseZeroOrMoreNodes(
        parseElement = ::parseShapeField,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        tokens = tokens,
        allowTrailingSeparator = true
    )

    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)
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
    tokens.skip(TokenType.KEYWORD_FUN)

    val name = parseIdentifier(tokens)

    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val arguments = parseZeroOrMoreNodes(
        parseElement = ::parseFormalArgument,
        parseSeparator = {tokens -> tokens.skip(TokenType.SYMBOL_COMMA)},
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
    tokens.skip(TokenType.SYMBOL_ARROW)
    val returnType = ::parseType.parse(tokens)
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
    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)
    val body = parseZeroOrMore(
        parseElement = ::parseFunctionStatement,
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)
    return body
}

private fun parseFormalArgument(source: Source, tokens: TokenIterator<TokenType>) : ArgumentNode {
    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return ArgumentNode(name, type, source)
}

private fun parseTypeSpec(tokens: TokenIterator<TokenType>): TypeNode {
    tokens.skip(TokenType.SYMBOL_COLON)
    return ::parseType.parse(tokens)
}

internal fun parseFunctionStatement(tokens: TokenIterator<TokenType>) : StatementNode {
    val token = tokens.peek()
    when (token.tokenType) {
        TokenType.KEYWORD_RETURN -> return ::parseReturn.parse(tokens)
        TokenType.KEYWORD_IF -> return ::parseIfStatement.parse(tokens)
        TokenType.KEYWORD_VAL -> return ::parseVal.parse(tokens)
        else -> {
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
    }
}

private fun tryParseExpressionStatement(source: Source, tokens: TokenIterator<TokenType>) : ExpressionStatementNode? {
    val expression = tryParseExpression(tokens)
    if (expression == null) {
        return null
    } else {
        tokens.skip(TokenType.SYMBOL_SEMICOLON)
        return ExpressionStatementNode(expression, source)
    }
}

private fun parseReturn(source: Source, tokens: TokenIterator<TokenType>) : ReturnNode {
    tokens.skip(TokenType.KEYWORD_RETURN)
    val expression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_SEMICOLON)
    return ReturnNode(expression, source)
}

private fun parseIfStatement(source: Source, tokens: TokenIterator<TokenType>) : IfStatementNode {
    tokens.skip(TokenType.KEYWORD_IF)
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val condition = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    val trueBranch = parseFunctionStatements(tokens)

    tokens.skip(TokenType.KEYWORD_ELSE)
    val falseBranch = parseFunctionStatements(tokens)

    return IfStatementNode(
        condition = condition,
        trueBranch = trueBranch,
        falseBranch = falseBranch,
        source = source
    )
}

private fun parseVal(source: Source, tokens: TokenIterator<TokenType>): ValNode {
    tokens.skip(TokenType.KEYWORD_VAL)
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL_EQUALS)
    val expression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_SEMICOLON)

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
        val operationParser = lookupOperator(next.tokenType)
        if (operationParser == null || operationParser.precedence < precedence) {
            return left
        } else {
            tokens.skip()
            left = operationParser.parse(left, tokens)
        }
    }
}

private fun lookupOperator(tokenType: TokenType) : OperationParser? {
    return when (tokenType) {
        TokenType.SYMBOL_DOUBLE_EQUALS -> OperationParser.EQUALS
        TokenType.SYMBOL_PLUS -> OperationParser.ADD
        TokenType.SYMBOL_MINUS -> OperationParser.SUBTRACT
        TokenType.SYMBOL_ASTERISK -> OperationParser.MULTIPLY
        TokenType.SYMBOL_OPEN_PAREN -> OperationParser.CALL
        TokenType.SYMBOL_DOT -> OperationParser.FIELD_ACCESS
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
            parseElement = { tokens -> ::parseArgument.parse(tokens) },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
            tokens = tokens
        )
        // TODO: check named arguments appear after all positional arguments
        tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
        val positionalArguments = arguments
            .filterIsInstance<ParsedArgument.Positional>()
            .map(ParsedArgument.Positional::expression)
        val namedArguments = arguments
            .filterIsInstance<ParsedArgument.Named>()
            .map(ParsedArgument.Named::node)
        return CallNode(
            receiver = left,
            positionalArguments = positionalArguments,
            namedArguments = namedArguments,
            source = left.source
        )
    }

    override val precedence: Int
        get() = 14
}

private sealed class ParsedArgument {
    class Positional(val expression: ExpressionNode): ParsedArgument()
    class Named(val node: CallNamedArgumentNode): ParsedArgument()
}

private fun parseArgument(source: Source, tokens: TokenIterator<TokenType>): ParsedArgument {
    if (tokens.isNext(TokenType.IDENTIFIER) && tokens.isNext(TokenType.SYMBOL_EQUALS, skip = 1)) {
        val name = parseIdentifier(tokens)
        tokens.skip(TokenType.SYMBOL_EQUALS)
        val expression = parseExpression(tokens)
        return ParsedArgument.Named(CallNamedArgumentNode(
            name = name,
            expression = expression,
            source = source
        ))
    } else {
        val expression = parseExpression(tokens)
        return ParsedArgument.Positional(expression)
    }
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
        TokenType.KEYWORD_TRUE -> return BooleanLiteralNode(true, source)
        TokenType.KEYWORD_FALSE -> return BooleanLiteralNode(false, source)
        TokenType.STRING -> return StringLiteralNode(
            decodeEscapeSequence(token.value.substring(1, token.value.length - 1), source = source),
            source
        )
        TokenType.SYMBOL_OPEN_PAREN -> {
            val expression = parseExpression(tokens)
            tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
            return expression
        }
        else -> return null
    }
}

private fun decodeEscapeSequence(value: String, source: Source): String {
    return decodeEscapeSequence(CharBuffer.wrap(value), source = source)
}

private val STRING_ESCAPE_PATTERN = Pattern.compile("\\\\(.)")

private fun decodeEscapeSequence(value: CharBuffer, source: Source): String {
    val matcher = STRING_ESCAPE_PATTERN.matcher(value)
    val decoded = StringBuilder()
    var lastIndex = 0
    while (matcher.find()) {
        decoded.append(value.subSequence(lastIndex, matcher.start()))
        decoded.append(escapeSequence(matcher.group(1), source = source))
        lastIndex = matcher.end()
    }
    decoded.append(value.subSequence(lastIndex, value.length))
    return decoded.toString()
}

private fun escapeSequence(code: String, source: Source): Char {
    when (code) {
        "n" -> return '\n'
        "r" -> return '\r'
        "t" -> return '\t'
        "\"" -> return '"'
        "\\" -> return '\\'
        else -> throw UnrecognisedEscapeSequenceError("\\" + code, source = source)
    }
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
