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
        .filter { token -> token.tokenType != TokenType.WHITESPACE && token.tokenType != TokenType.COMMENT }
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
    val imports = parseZeroOrMore(
        parseElement = { tokens -> ::parseImport.parse(tokens) },
        isEnd = { tokens -> !tokens.isNext(TokenType.KEYWORD_IMPORT) },
        tokens = tokens
    )
    val body = parseZeroOrMore(
        parseElement = ::parseModuleStatement,
        isEnd = { tokens -> tokens.isNext(TokenType.END) },
        tokens = tokens
    )
    return ModuleNode(
        path = moduleName.split("."),
        imports = imports,
        body = body,
        source = source
    )
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

internal fun parseImport(source: Source, tokens: TokenIterator<TokenType>): ImportNode {
    tokens.skip(TokenType.KEYWORD_IMPORT)
    val isLocal = tokens.trySkip(TokenType.SYMBOL_DOT)

    val moduleNameParts = parseMany(
        parseElement = ::parseIdentifier,
        parseSeparator = { tokens -> tokens.trySkip(TokenType.SYMBOL_DOT) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_SEMICOLON) },
        tokens = tokens,
        allowZero = false
    )
    tokens.skip(TokenType.SYMBOL_SEMICOLON)
    val path = ImportPath(
        base = if (isLocal) ImportPathBase.Relative else ImportPathBase.Absolute,
        parts = moduleNameParts
    )
    return ImportNode(path = path, source = source)
}

internal fun parseModuleStatement(tokens: TokenIterator<TokenType>): ModuleStatementNode {
    if (tokens.isNext(TokenType.KEYWORD_SHAPE)) {
        return ::parseShape.parse(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_UNION)) {
        return ::parseUnion.parse(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_FUN)) {
        return ::parseFunctionDeclaration.parse(tokens)
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
    val typeParameters = parseTypeParameters(tokens)
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
        typeParameters = typeParameters,
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

private fun parseUnion(source: Source, tokens: TokenIterator<TokenType>): UnionNode {
    tokens.skip(TokenType.KEYWORD_UNION)
    val name = parseIdentifier(tokens)
    val typeParameters = parseTypeParameters(tokens)

    tokens.skip(TokenType.SYMBOL_EQUALS)

    val members = parseMany(
        parseElement = { tokens -> ::parseType.parse(tokens) },
        parseSeparator = { tokens -> tokens.trySkip(TokenType.SYMBOL_BAR) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_SEMICOLON) },
        allowZero = false,
        tokens = tokens
    )

    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return UnionNode(
        name = name,
        typeParameters = typeParameters,
        members = members,
        source = source
    )
}

internal fun parseFunctionDeclaration(source: Source, tokens: TokenIterator<TokenType>): FunctionDeclarationNode {
    tokens.skip(TokenType.KEYWORD_FUN)
    val name = parseIdentifier(tokens)
    val function = parseFunction(tokens)
    return FunctionDeclarationNode(
        name = name,
        typeParameters = function.typeParameters,
        arguments = function.arguments,
        returnType = function.returnType,
        effects = function.effects,
        body = function.body,
        source = source
    )
}

private data class ParsedFunction(
    val typeParameters: List<TypeParameterNode>,
    val arguments: List<ArgumentNode>,
    val returnType: TypeNode,
    val effects: List<VariableReferenceNode>,
    val body: List<StatementNode>
)

private fun parseFunction(tokens: TokenIterator<TokenType>): ParsedFunction {
    val typeParameters = parseTypeParameters(tokens)

    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val arguments = parseZeroOrMoreNodes(
        parseElement = ::parseFormalArgument,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    val effects = parseZeroOrMoreNodes(
        parseElement = ::parseVariableReference,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_ARROW) },
        tokens = tokens
    )

    tokens.skip(TokenType.SYMBOL_ARROW)
    val returnType = ::parseType.parse(tokens)
    val body = parseFunctionStatements(tokens)

    return ParsedFunction(
        typeParameters = typeParameters,
        arguments = arguments,
        returnType = returnType,
        effects = effects,
        body = body
    )
}

private fun parseTypeParameters(tokens: TokenIterator<TokenType>): List<TypeParameterNode> {
    return if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
        val typeParameters = parseMany(
            parseElement = { tokens -> ::parseTypeParameter.parse(tokens) },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
            allowZero = false,
            allowTrailingSeparator = true,
            tokens = tokens
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
        typeParameters
    } else {
        listOf()
    }
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

private fun parseTypeParameter(source: Source, tokens: TokenIterator<TokenType>): TypeParameterNode {
    val name = parseIdentifier(tokens)
    return TypeParameterNode(name = name, source = source)
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


    val falseBranch = if (tokens.trySkip(TokenType.KEYWORD_ELSE)) {
        parseFunctionStatements(tokens)
    } else {
        listOf()
    }

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
        TokenType.KEYWORD_IS -> OperationParser.IS
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
        val IS = IsParser
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

private object IsParser : OperationParser {
    override val precedence: Int
        get() = 9

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val type = ::parseType.parse(tokens)
        return IsNode(
            expression = left,
            type = type,
            source = left.source
        )
    }
}

internal fun tryParsePrimaryExpression(source: Source, tokens: TokenIterator<TokenType>) : ExpressionNode? {
    val tokenType = tokens.peek().tokenType;
    when (tokenType) {
        TokenType.KEYWORD_UNIT -> {
            tokens.skip()
            return UnitLiteralNode(source)
        }
        TokenType.INTEGER -> {
            val token = tokens.next()
            return IntegerLiteralNode(token.value.toInt(), source)
        }
        TokenType.IDENTIFIER -> {
            return parseVariableReference(source, tokens)
        }
        TokenType.KEYWORD_TRUE -> {
            tokens.skip()
            return BooleanLiteralNode(true, source)
        }
        TokenType.KEYWORD_FALSE -> {
            tokens.skip()
            return BooleanLiteralNode(false, source)
        }
        TokenType.STRING -> {
            val token = tokens.next()
            val value = decodeEscapeSequence(token.value.substring(1, token.value.length - 1), source = source)
            return StringLiteralNode(value, source)
        }
        TokenType.SYMBOL_OPEN_PAREN -> {
            tokens.skip()
            val expression = parseExpression(tokens)
            tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
            return expression
        }
        TokenType.KEYWORD_FUN -> {
            tokens.skip()
            val function = parseFunction(tokens)
            return FunctionExpressionNode(
                typeParameters = function.typeParameters,
                arguments = function.arguments,
                returnType = function.returnType,
                effects = function.effects,
                body = function.body,
                source = source
            )
        }
        else -> return null
    }
}

private fun parseVariableReference(source: Source, tokens: TokenIterator<TokenType>): VariableReferenceNode {
    val value = tokens.nextValue(TokenType.IDENTIFIER)
    return VariableReferenceNode(value, source)
}

private fun parseVariableReference(source: Source, token: Token<TokenType>): VariableReferenceNode {
    return VariableReferenceNode(token.value, source)
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
    if (tokens.isNext(TokenType.SYMBOL_OPEN_PAREN)) {
        return parseFunctionType(source, tokens)
    } else {
        val name = parseIdentifier(tokens)
        val typeReference = TypeReferenceNode(name, source)
        if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
            val arguments = parseMany(
                parseElement = { tokens -> ::parseType.parse(tokens) },
                parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA)},
                allowZero = false,
                allowTrailingSeparator = true,
                isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
                tokens = tokens
            )
            tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
            return TypeApplicationNode(receiver = typeReference, arguments = arguments, source = source)
        } else {
            return typeReference
        }
    }
}

private fun parseFunctionType(source: Source, tokens: TokenIterator<TokenType>): TypeNode {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val arguments = parseMany(
        parseElement = { tokens -> ::parseType.parse(tokens) },
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        allowZero = true,
        allowTrailingSeparator = true,
        isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
    tokens.skip(TokenType.SYMBOL_ARROW)
    val returnType = ::parseType.parse(tokens)
    return FunctionTypeNode(
        arguments = arguments,
        returnType = returnType,
        source = source
    )
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
    return parseMany(
        parseElement = parseElement,
        parseSeparator = parseSeparator,
        isEnd = isEnd,
        tokens = tokens,
        allowTrailingSeparator = allowTrailingSeparator,
        allowZero = true
    )
}

private fun <T> parseMany(
    parseElement: (TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Unit = { tokens -> },
    isEnd: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>,
    allowTrailingSeparator: Boolean = false,
    allowZero: Boolean
) : List<T> {
    if (allowZero && isEnd(tokens)) {
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
