package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.MissingReturnTypeError
import org.shedlang.compiler.typechecker.SourceError
import org.shedlang.compiler.types.Variance
import java.nio.CharBuffer
import java.util.regex.Pattern

internal open class InvalidCharacter(
    source: Source,
    message: String
) : SourceError(message, source = source)

internal class UnrecognisedEscapeSequenceError(
    val escapeSequence: String,
    source: Source
) : InvalidCharacter(
    source = source,
    message = "Unrecognised escape sequence"
)

internal class InvalidCharacterLiteral(message: String, source: Source) : SourceError(
    message = message,
    source = source
)

fun parse(filename: String, input: String): ModuleNode {
    return parse(
        filename = filename,
        input = input,
        rule = { tokens -> ::parseModule.parse(tokens) }
    )
}

internal fun parseTypesModule(filename: String, input: String): TypesModuleNode {
    return parse(
        filename = filename,
        input = input,
        rule = { tokens -> ::parseTypesModuleTokens.parse(tokens) }
    )
}

internal fun <T> parse(
    filename: String,
    input: String,
    rule: (TokenIterator<TokenType>) -> T
): T {
    val tokenIterator = parserTokenise(filename, input)
    val node = rule(tokenIterator)
    tokenIterator.skip(TokenType.END)
    return node
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

internal fun <T> ((StringSource, TokenIterator<TokenType>) -> T).parse(tokens: TokenIterator<TokenType>): T {
    val source = tokens.location()
    return this(source, tokens)
}

internal fun parseModule(source: StringSource, tokens: TokenIterator<TokenType>): ModuleNode {
    val imports = parseImports(tokens)
    val body = parseZeroOrMore(
        parseElement = ::parseModuleStatement,
        isEnd = { tokens -> tokens.isNext(TokenType.END) },
        tokens = tokens
    )
    return ModuleNode(
        imports = imports,
        body = body,
        source = source
    )
}

internal fun parseTypesModuleTokens(source: StringSource, tokens: TokenIterator<TokenType>): TypesModuleNode {
    val imports = parseImports(tokens)
    val body = parseZeroOrMore(
        parseElement = { tokens -> ::parseValType.parse(tokens) },
        isEnd = { tokens -> tokens.isNext(TokenType.END) },
        tokens = tokens
    )

    return TypesModuleNode(
        imports = imports,
        body = body,
        source = source
    )
}

private fun parseImports(tokens: TokenIterator<TokenType>): List<ImportNode> {
    return parseZeroOrMore(
        parseElement = { tokens -> ::parseImport.parse(tokens) },
        isEnd = { tokens -> !tokens.isNext(TokenType.KEYWORD_IMPORT) },
        tokens = tokens
    )
}

private fun parseValType(source: StringSource, tokens: TokenIterator<TokenType>): ValTypeNode {
    tokens.skip(TokenType.KEYWORD_VAL)
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL_COLON)
    val type = parseStaticExpression(tokens)
    tokens.skip(TokenType.SYMBOL_SEMICOLON)
    return ValTypeNode(
        name = name,
        type = type,
        source = source
    )
}

internal fun parseImport(source: StringSource, tokens: TokenIterator<TokenType>): ImportNode {
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
    } else if (tokens.isNext(TokenType.KEYWORD_VAL)) {
        return ::parseVal.parse(tokens)
    } else {
        throw UnexpectedTokenException(
            expected = "module statement",
            actual = tokens.peek().describe(),
            location = tokens.location()
        )
    }
}

internal fun parseShape(source: StringSource, tokens: TokenIterator<TokenType>): ShapeNode {
    tokens.skip(TokenType.KEYWORD_SHAPE)
    val name = parseIdentifier(tokens)
    val staticParameters = parseStaticParameters(allowVariance = true, tokens = tokens)

    val tagged = tokens.trySkip(TokenType.KEYWORD_TAGGED)
    val tagValueFor = if (tokens.trySkip(TokenType.KEYWORD_MEMBER_OF)) {
        parseStaticExpression(tokens)
    } else {
        null
    }

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
        staticParameters = staticParameters,
        tagged = tagged,
        hasTagValueFor = tagValueFor,
        fields = fields,
        source = source
    )
}

private fun parseShapeField(source: StringSource, tokens: TokenIterator<TokenType>): ShapeFieldNode {
    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return ShapeFieldNode(
        name = name,
        type = type,
        source = source
    )
}

private fun parseUnion(source: StringSource, tokens: TokenIterator<TokenType>): UnionNode {
    tokens.skip(TokenType.KEYWORD_UNION)
    val name = parseIdentifier(tokens)
    val staticParameters = parseStaticParameters(allowVariance = true, tokens = tokens)

    val explicitTag = if (tokens.trySkip(TokenType.SYMBOL_SUBTYPE)) {
         ::parseStaticReference.parse(tokens)
    } else {
        null
    }

    tokens.skip(TokenType.SYMBOL_EQUALS)

    val members = parseMany(
        parseElement = { tokens -> parseStaticExpression(tokens) },
        parseSeparator = { tokens -> tokens.trySkip(TokenType.SYMBOL_BAR) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_SEMICOLON) },
        allowZero = false,
        tokens = tokens
    )

    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return UnionNode(
        name = name,
        staticParameters = staticParameters,
        superType = explicitTag,
        members = members,
        source = source
    )
}

internal fun parseFunctionDeclaration(source: StringSource, tokens: TokenIterator<TokenType>): FunctionDeclarationNode {
    tokens.skip(TokenType.KEYWORD_FUN)
    val name = parseIdentifier(tokens)
    val signature = parseFunctionSignature(tokens)
    val body = parseFunctionStatements(tokens)

    if (signature.returnType == null) {
        throw MissingReturnTypeError("Function declaration must have return type", source = source)
    } else {
        return FunctionDeclarationNode(
            name = name,
            staticParameters = signature.staticParameters,
            parameters = signature.parameters,
            namedParameters = signature.namedParameters,
            returnType = signature.returnType,
            effects = signature.effects,
            body = FunctionBody.Statements(body),
            source = source
        )
    }
}

private data class FunctionSignature(
    val staticParameters: List<StaticParameterNode>,
    val parameters: List<ParameterNode>,
    val namedParameters: List<ParameterNode>,
    val effects: List<StaticNode>,
    val returnType: StaticNode?
)

private fun parseFunctionSignature(tokens: TokenIterator<TokenType>): FunctionSignature {
    val staticParameters = parseStaticParameters(allowVariance = false, tokens = tokens)

    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val parameters = parseZeroOrMore(
        parseElement = ::parseParametersPart,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        tokens = tokens
    )

    // TODO: make sure there's only one start of named parameters

    val namedStartIndex = parameters.indexOfFirst { parameter ->
        parameter == Parameter.StartOfNamedParameters
    }

    val (positionalParameters, namedParameters) = if (namedStartIndex == -1) {
        Pair(parameters, listOf<ParameterNode>())
    } else {
        Pair(parameters.take(namedStartIndex), parameters.drop(namedStartIndex + 1))
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    // TODO: allow trailing commas?
    val effects = parseEffects(tokens)

    val returnType = if (tokens.trySkip(TokenType.SYMBOL_ARROW)) {
        parseStaticExpression(tokens)
    } else {
        null
    }

    return FunctionSignature(
        staticParameters = staticParameters,
        parameters = positionalParameters.map { parameter -> (parameter as Parameter.Node).parameter },
        namedParameters = namedParameters.map { parameter -> (parameter as Parameter.Node).parameter },
        effects = effects,
        returnType = returnType
    )
}

internal fun parseStaticParameters(
    allowVariance: Boolean,
    tokens: TokenIterator<TokenType>
): List<StaticParameterNode> {
    return if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
        val typeParameters = parseMany(
            parseElement = { tokens -> parseStaticParameter(allowVariance = allowVariance).parse(tokens) },
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

private fun parseEffects(tokens: TokenIterator<TokenType>): List<StaticNode> {
    return parseZeroOrMore(
        parseElement = ::parseEffect,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = {
            tokens.isNext(TokenType.SYMBOL_ARROW) ||
                tokens.isNext(TokenType.SYMBOL_FAT_ARROW) ||
                tokens.isNext(TokenType.SYMBOL_OPEN_BRACE)
        },
        tokens = tokens
    )
}

private fun parseEffect(tokens: TokenIterator<TokenType>): StaticNode {
    tokens.skip(TokenType.SYMBOL_BANG)
    return parseStaticExpression(tokens)
}

private fun parseFunctionStatements(tokens: TokenIterator<TokenType>): List<StatementNode> {
    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)

    val statements = mutableListOf<StatementNode>()
    var lastStatement: StatementNode? = null

    while (!(tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) || (lastStatement != null && lastStatement.isReturn))) {
        lastStatement = parseFunctionStatement(tokens)
        statements.add(lastStatement)
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)
    return statements
}

private fun parseStaticParameter(allowVariance: Boolean) = fun (source: StringSource, tokens: TokenIterator<TokenType>): StaticParameterNode {
    if (tokens.trySkip(TokenType.SYMBOL_BANG)) {
        val name = parseIdentifier(tokens)
        return EffectParameterNode(
            name = name,
            source = source
        )
    } else {
        val variance = if (!allowVariance) {
            Variance.INVARIANT
        } else if (tokens.trySkip(TokenType.SYMBOL_PLUS)) {
            Variance.COVARIANT
        } else if (tokens.trySkip(TokenType.SYMBOL_MINUS)) {
            Variance.CONTRAVARIANT
        } else {
            Variance.INVARIANT
        }
        val name = parseIdentifier(tokens)
        return TypeParameterNode(name = name, variance = variance, source = source)
    }
}

private sealed class Parameter {
    class Node(val parameter: ParameterNode): Parameter()
    object StartOfNamedParameters : Parameter()
}

private fun parseParametersPart(tokens: TokenIterator<TokenType>) : Parameter {
    if (tokens.trySkip(TokenType.SYMBOL_ASTERISK)) {
        return Parameter.StartOfNamedParameters
    } else {
        return Parameter.Node(::parseParameter.parse(tokens))
    }
}

private fun parseParameter(source: StringSource, tokens: TokenIterator<TokenType>): ParameterNode {
    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return ParameterNode(name, type, source)
}

private fun parseTypeSpec(tokens: TokenIterator<TokenType>): StaticNode {
    tokens.skip(TokenType.SYMBOL_COLON)
    return parseStaticExpression(tokens)
}

internal fun parseFunctionStatement(tokens: TokenIterator<TokenType>) : StatementNode {
    val token = tokens.peek()
    when (token.tokenType) {
        TokenType.KEYWORD_VAL -> return ::parseVal.parse(tokens)
        else -> {
            val expression = tryParseExpression(tokens)
            if (expression == null) {
                throw UnexpectedTokenException(
                    location = tokens.location(),
                    expected = "function statement",
                    actual = token.describe()
                )
            } else {
                val isReturn = if (expression is IfNode) {
                    branchesReturn(expression, expression.branchBodies)
                } else if (expression is WhenNode) {
                    branchesReturn(expression, expression.branches.map { branch -> branch.body })
                } else {
                    !tokens.trySkip(TokenType.SYMBOL_SEMICOLON)
                }
                return ExpressionStatementNode(
                    expression,
                    isReturn = isReturn,
                    source = expression.source
                )
            }
        }
    }
}

private fun branchesReturn(expression: Node, branches: Iterable<List<StatementNode>>): Boolean {
    val isReturns = branches.map { body ->
        body.any(StatementNode::isReturn)
    }.toSet()
    return if (isReturns.size == 1) {
        isReturns.single()
    } else {
        // TODO: raise a more specific exception
        // TODO: should these checks be in the expression nodes themselves?
        throw SourceError("Some branches do not provide a value", source = expression.source)
    }
}

private fun parseIf(source: StringSource, tokens: TokenIterator<TokenType>) : IfNode {
    val conditionalBranches = parseConditionalBranches(tokens, source)

    val elseBranch = if (tokens.trySkip(TokenType.KEYWORD_ELSE)) {
        parseFunctionStatements(tokens)
    } else {
        listOf()
    }

    return IfNode(
        conditionalBranches = conditionalBranches,
        elseBranch = elseBranch,
        source = source
    )
}

private fun parseConditionalBranches(tokens: TokenIterator<TokenType>, source: StringSource): List<ConditionalBranchNode> {
    tokens.skip(TokenType.KEYWORD_IF)
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val condition = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
    val trueBranch = parseFunctionStatements(tokens)

    return listOf(ConditionalBranchNode(
        condition = condition,
        body = trueBranch,
        source = source
    )) + parseAdditionalConditionalBranches(tokens)
}

private fun parseAdditionalConditionalBranches(tokens: TokenIterator<TokenType>): List<ConditionalBranchNode> {
    val branches = mutableListOf<ConditionalBranchNode>()

    while (true) {
        val branch = ::tryParseAdditionalConditionalBranch.parse(tokens)
        if (branch == null) {
            return branches
        } else {
            branches.add(branch)
        }
    }
}

private fun tryParseAdditionalConditionalBranch(
    source: StringSource,
    tokens: TokenIterator<TokenType>
): ConditionalBranchNode? {
    if (tokens.trySkip(listOf(TokenType.KEYWORD_ELSE, TokenType.KEYWORD_IF))) {
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        val condition = parseExpression(tokens)
        tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
        val body = parseFunctionStatements(tokens)
        return ConditionalBranchNode(
            condition = condition,
            body = body,
            source = source
        )
    } else {
        return null
    }
}

private fun parseWhen(source: StringSource, tokens: TokenIterator<TokenType>): WhenNode {
    tokens.skip(TokenType.KEYWORD_WHEN)

    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val expression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)
    val branches = parseMany(
        parseElement = { tokens -> ::parseWhenBranch.parse(tokens) },
        isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        tokens = tokens,
        allowZero = true
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

    return WhenNode(
        expression = expression,
        branches = branches,
        source = source
    )
}

private fun parseWhenBranch(source: StringSource, tokens: TokenIterator<TokenType>): WhenBranchNode {
    tokens.skip(TokenType.KEYWORD_IS)
    val type = parseStaticExpression(tokens)
    val body = parseFunctionStatements(tokens)
    return WhenBranchNode(
        type = type,
        body = body,
        source = source
    )
}

private fun parseVal(source: StringSource, tokens: TokenIterator<TokenType>): ValNode {
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
        val operationParser = OperationParser.lookup(next.tokenType)
        if (operationParser == null || operationParser.precedence < precedence) {
            return left
        } else {
            tokens.skip()
            left = operationParser.parse(left, tokens)
        }
    }
}

private val PIPELINE_PRECEDENCE = 7
private val COMPARISON_PRECEDENCE = 8
private val IS_PRECEDENCE = 9
private val ADD_PRECENDECE = 11
private val SUBTRACT_PRECEDENCE = 11
private val MULTIPLY_PRECEDENCE = 12
private val CALL_PRECEDENCE = 14
private val FIELD_ACCESS_PRECEDENCE = 14

private interface OperationParser: ExpressionParser<ExpressionNode> {
    companion object {
        val parsers = listOf(
            InfixOperationParser(Operator.EQUALS, TokenType.SYMBOL_DOUBLE_EQUALS, COMPARISON_PRECEDENCE),
            InfixOperationParser(Operator.LESS_THAN, TokenType.SYMBOL_LESS_THAN, COMPARISON_PRECEDENCE),
            InfixOperationParser(Operator.LESS_THAN_OR_EQUAL, TokenType.SYMBOL_LESS_THAN_OR_EQUAL, COMPARISON_PRECEDENCE),
            InfixOperationParser(Operator.GREATER_THAN, TokenType.SYMBOL_GREATER_THAN, COMPARISON_PRECEDENCE),
            InfixOperationParser(Operator.GREATER_THAN_OR_EQUAL, TokenType.SYMBOL_GREATER_THAN_OR_EQUAL, COMPARISON_PRECEDENCE),
            InfixOperationParser(Operator.ADD, TokenType.SYMBOL_PLUS, ADD_PRECENDECE),
            InfixOperationParser(Operator.SUBTRACT, TokenType.SYMBOL_MINUS, SUBTRACT_PRECEDENCE),
            InfixOperationParser(Operator.MULTIPLY, TokenType.SYMBOL_ASTERISK, MULTIPLY_PRECEDENCE),
            CallWithExplicitTypeArgumentsParser,
            CallParser,
            PartialCallParser,
            PipelineParser,
            FieldAccessParser,
            IsParser
        ).associateBy({parser -> parser.operatorToken})

        fun lookup(tokenType: TokenType): OperationParser? {
            return parsers[tokenType]
        }
    }
}

private class InfixOperationParser(
    private val operator: Operator,
    override val operatorToken: TokenType,
    override val precedence: Int
) : OperationParser {
    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val right = parseExpression(tokens, precedence + 1)
        return BinaryOperationNode(operator, left, right, left.source)
    }
}

private object CallWithExplicitTypeArgumentsParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_OPEN_SQUARE_BRACKET

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val typeArguments = parseMany(
            parseElement = { tokens -> parseStaticExpression(tokens) },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
            allowZero = false,
            tokens = tokens
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        return parseCallFromParens(
            left = left,
            typeArguments = typeArguments,
            tokens = tokens
        )
    }

    override val precedence: Int
        get() = CALL_PRECEDENCE
}

private object CallParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_OPEN_PAREN

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        return parseCallFromParens(
            left = left,
            typeArguments = listOf(),
            tokens = tokens
        )
    }

    override val precedence: Int
        get() = CALL_PRECEDENCE
}

private object PartialCallParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_TILDE

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        val (positionalArguments, namedArguments) = parseCallArguments(tokens)
        return PartialCallNode(
            receiver = left,
            staticArguments = listOf(),
            positionalArguments = positionalArguments,
            namedArguments = namedArguments,
            source = left.source
        )
    }

    override val precedence: Int
        get() = CALL_PRECEDENCE
}

private fun parseCallFromParens(
    left: ExpressionNode,
    typeArguments: List<StaticNode>,
    tokens: TokenIterator<TokenType>
): CallNode {
    val (positionalArguments, namedArguments) = parseCallArguments(tokens)
    return CallNode(
        receiver = left,
        staticArguments = typeArguments,
        positionalArguments = positionalArguments,
        namedArguments = namedArguments,
        source = left.source
    )
}

private fun parseCallArguments(tokens: TokenIterator<TokenType>): Pair<List<ExpressionNode>, List<CallNamedArgumentNode>> {
    val arguments = parseMany(
        parseElement = { tokens -> ::parseArgument.parse(tokens) },
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        tokens = tokens,
        allowZero = true,
        allowTrailingSeparator = true
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    val positionalArguments = mutableListOf<ExpressionNode>()
    val namedArguments = mutableListOf<CallNamedArgumentNode>()

    for (argument in arguments) {
        when (argument) {
            is ParsedArgument.Positional -> {
                if (namedArguments.isEmpty()) {
                    positionalArguments.add(argument.expression)
                } else {
                    throw ParseError("Positional argument cannot appear after named argument", location = argument.expression.source)
                }
            }
            is ParsedArgument.Named -> namedArguments.add(argument.node)
        }
    }

    return Pair(positionalArguments, namedArguments)
}

private sealed class ParsedArgument {
    class Positional(val expression: ExpressionNode): ParsedArgument()
    class Named(val node: CallNamedArgumentNode): ParsedArgument()
}

private fun parseArgument(source: StringSource, tokens: TokenIterator<TokenType>): ParsedArgument {
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

private object PipelineParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_PIPELINE

    override val precedence: Int
        get()  = PIPELINE_PRECEDENCE

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val right = parseExpression(tokens, precedence = precedence + 1)
        return CallNode(
            receiver = right,
            positionalArguments = listOf(left),
            namedArguments = listOf(),
            staticArguments = listOf(),
            source = left.source
        )
    }
}

private object FieldAccessParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_DOT

    override val precedence: Int
        get() = FIELD_ACCESS_PRECEDENCE

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
    override val operatorToken: TokenType
        get() = TokenType.KEYWORD_IS

    override val precedence: Int
        get() = IS_PRECEDENCE

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val type = parseStaticExpression(tokens)
        return IsNode(
            expression = left,
            type = type,
            source = left.source
        )
    }
}

internal fun tryParsePrimaryExpression(source: StringSource, tokens: TokenIterator<TokenType>) : ExpressionNode? {
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
            val value = decodeCharacterToken(token.value, source = source)
            return StringLiteralNode(value, source)
        }
        TokenType.CHARACTER -> {
            val token = tokens.next()
            val stringValue = decodeCharacterToken(token.value, source = source)
            // TODO: handle surrogate pairs
            if (stringValue.length == 1) {
                val value = stringValue[0].toInt()
                return CharacterLiteralNode(value, source)
            } else {
                throw InvalidCharacterLiteral("Character literal has ${stringValue.length} characters", source = source)
            }
        }
        TokenType.SYMBOL_OPEN_PAREN -> {
            tokens.skip()
            val expression = parseExpression(tokens)
            tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
            return expression
        }
        TokenType.KEYWORD_IF -> {
            return ::parseIf.parse(tokens)
        }
        TokenType.KEYWORD_WHEN -> {
            return ::parseWhen.parse(tokens)
        }
        TokenType.KEYWORD_FUN -> {
            tokens.skip()
            val signature = parseFunctionSignature(tokens)

            val body = if (tokens.trySkip(TokenType.SYMBOL_FAT_ARROW)) {
                FunctionBody.Expression(parseExpression(tokens))
            } else {
                FunctionBody.Statements(parseFunctionStatements(tokens))
            }

            return FunctionExpressionNode(
                staticParameters = signature.staticParameters,
                parameters = signature.parameters,
                namedParameters = signature.namedParameters,
                returnType = signature.returnType,
                effects = signature.effects,
                body = body,
                source = source
            )
        }
        else -> return null
    }
}

private fun parseVariableReference(source: StringSource, tokens: TokenIterator<TokenType>): VariableReferenceNode {
    val value = parseIdentifier(tokens)
    return VariableReferenceNode(value, source)
}

private fun decodeCharacterToken(value: String, source: StringSource): String {
    return decodeEscapeSequence(value.substring(1, value.length - 1), source = source)
}

private fun decodeEscapeSequence(value: String, source: StringSource): String {
    return decodeEscapeSequence(CharBuffer.wrap(value), source = source)
}

private val STRING_ESCAPE_PATTERN = Pattern.compile("\\\\(.)")

private fun decodeEscapeSequence(value: CharBuffer, source: StringSource): String {
    val matcher = STRING_ESCAPE_PATTERN.matcher(value)
    val decoded = StringBuilder()
    var lastIndex = 0
    while (matcher.find()) {
        decoded.append(value.subSequence(lastIndex, matcher.start()))
        val code = matcher.group(1)
        if (code == "u") {
            if (value[matcher.end()] != '{') {
                throw InvalidCharacter(
                    source = source.at(matcher.end() + 1),
                    message = "Expected opening brace"
                )
            }
            val startIndex = matcher.end() + 1
            val endIndex = value.indexOf("}", startIndex = startIndex)
            if (endIndex == -1) {
                throw InvalidCharacter(
                    source = source.at(matcher.end() + 1),
                    message = "Could not find closing brace"
                )
            }
            val hex = value.subSequence(startIndex, endIndex).toString()
            val codePoint = hex.toInt(16)
            decoded.append(codePoint.toChar())
            lastIndex = endIndex + 1
        } else {
            decoded.append(escapeSequence(code, source = source))
            lastIndex = matcher.end()
        }
    }
    decoded.append(value.subSequence(lastIndex, value.length))
    return decoded.toString()
}

private fun escapeSequence(code: String, source: StringSource): Char {
    when (code) {
        "n" -> return '\n'
        "r" -> return '\r'
        "t" -> return '\t'
        "\"" -> return '"'
        "'" -> return '\''
        "\\" -> return '\\'
        else -> throw UnrecognisedEscapeSequenceError("\\" + code, source = source)
    }
}

internal fun parseStaticExpression(tokens: TokenIterator<TokenType>) : StaticNode {
    return parseStaticExpression(tokens = tokens, precedence = Int.MIN_VALUE)
}

private fun parseStaticExpression(
    tokens: TokenIterator<TokenType>,
    precedence: Int
) : StaticNode {
    var left: StaticNode = ::parsePrimaryStaticExpression.parse(tokens = tokens)
    while (true) {
        val next = tokens.peek()
        val operationParser = StaticOperationParser.lookup(next.tokenType)
        if (operationParser == null || operationParser.precedence < precedence) {
            return left
        } else {
            tokens.skip()
            left = operationParser.parse(left, tokens)
        }
    }
}

private fun parsePrimaryStaticExpression(
    source: StringSource,
    tokens: TokenIterator<TokenType>
): StaticNode {
    if (tokens.isNext(TokenType.SYMBOL_OPEN_SQUARE_BRACKET) || tokens.isNext(TokenType.SYMBOL_OPEN_PAREN)) {
        return parseFunctionType(source, tokens)
    } else {
        return parseStaticReference(source, tokens)
    }
}

private fun parseStaticReference(source: StringSource, tokens: TokenIterator<TokenType>): StaticReferenceNode {
    val name = parseIdentifier(tokens)
    return StaticReferenceNode(name, source)
}

private fun parseFunctionType(source: StringSource, tokens: TokenIterator<TokenType>): StaticNode {
    val staticParameters = parseStaticParameters(allowVariance = true, tokens = tokens)
    val parameters = parseFunctionTypeParameters(tokens)

    val effects = parseEffects(tokens)
    tokens.skip(TokenType.SYMBOL_ARROW)
    val returnType = parseStaticExpression(tokens)
    return FunctionTypeNode(
        staticParameters = staticParameters,
        positionalParameters = parameters.positional,
        namedParameters = parameters.named,
        returnType = returnType,
        effects = effects,
        source = source
    )
}

private class FunctionTypeParameters(
    val positional: List<StaticNode>,
    val named: List<ParameterNode>
)

private fun parseFunctionTypeParameters(tokens: TokenIterator<TokenType>): FunctionTypeParameters {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)

    val positionalParameters = mutableListOf<StaticNode>()
    val namedParameters = mutableListOf<ParameterNode>()
    var named = false

    while (true) {
        if (tokens.trySkip(TokenType.SYMBOL_CLOSE_PAREN)) {
            return FunctionTypeParameters(
                positional = positionalParameters,
                named = namedParameters
            )
        }

        if (tokens.trySkip(TokenType.SYMBOL_ASTERISK)) {
            named = true
        } else if (named) {
            val parameter = ::parseParameter.parse(tokens)
            namedParameters.add(parameter)
        } else {
            val parameter = parseStaticExpression(tokens)
            positionalParameters.add(parameter)
        }

        if (tokens.trySkip(TokenType.SYMBOL_CLOSE_PAREN)) {
            return FunctionTypeParameters(
                positional = positionalParameters,
                named = namedParameters
            )
        }

        tokens.skip(TokenType.SYMBOL_COMMA)
    }
}

private interface StaticOperationParser: ExpressionParser<StaticNode> {
    companion object {
        private val parsers = listOf(
            TypeApplicationParser,
            StaticFieldAccessParser
        ).associateBy({ parser -> parser.operatorToken })

        fun lookup(tokenType: TokenType): StaticOperationParser? {
            return parsers[tokenType]
        }
    }
}

private interface ExpressionParser<T> {
    val precedence: Int
    val operatorToken: TokenType

    fun parse(left: T, tokens: TokenIterator<TokenType>): T
}

private object TypeApplicationParser : StaticOperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_OPEN_SQUARE_BRACKET

    override fun parse(left: StaticNode, tokens: TokenIterator<TokenType>): StaticNode {
        val arguments = parseMany(
            parseElement = { tokens -> parseStaticExpression(tokens) },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA)},
            allowZero = false,
            allowTrailingSeparator = true,
            isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
            tokens = tokens
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
        return StaticApplicationNode(receiver = left, arguments = arguments, source = left.source)
    }

    override val precedence: Int
        get() = 14
}

private object StaticFieldAccessParser : StaticOperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_DOT

    override fun parse(left: StaticNode, tokens: TokenIterator<TokenType>): StaticNode {
        val fieldName = parseIdentifier(tokens)
        return StaticFieldAccessNode(receiver = left, fieldName = fieldName, source = left.source)
    }

    override val precedence: Int
        get() = 14
}

private fun parseIdentifier(tokens: TokenIterator<TokenType>) = Identifier(tokens.nextValue(TokenType.IDENTIFIER))

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
    parseElement: (StringSource, TokenIterator<TokenType>) -> T,
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
    return parseMany(
        parseElement = parseElement,
        parseSeparator = parseSeparator,
        isEnd = isEnd,
        tokens = tokens,
        allowTrailingSeparator = allowTrailingSeparator,
        allowZero = allowZero,
        initial = mutableListOf<T>(),
        reduce = { elements, element -> elements.add(element); elements }
    )
}

private fun <T, R> parseMany(
    parseElement: (TokenIterator<TokenType>) -> T,
    parseSeparator: (TokenIterator<TokenType>) -> Unit,
    isEnd: (TokenIterator<TokenType>) -> Boolean,
    tokens: TokenIterator<TokenType>,
    allowTrailingSeparator: Boolean,
    allowZero: Boolean,
    initial: R,
    reduce: (R, T) -> R
) : R {
    if (allowZero && isEnd(tokens)) {
        return initial
    }

    var result = initial

    while (true) {
        result = reduce(result, parseElement(tokens))
        if (isEnd(tokens)) {
            return result
        }
        parseSeparator(tokens)
        if (allowTrailingSeparator && isEnd(tokens)) {
            return result
        }
    }
}
