package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.MissingReturnTypeError
import org.shedlang.compiler.typechecker.SourceError
import org.shedlang.compiler.types.Variance
import java.nio.CharBuffer
import java.util.regex.Pattern

internal open class InvalidUnicodeScalar(
    source: Source,
    message: String
) : SourceError(message, source = source)

internal class UnrecognisedEscapeSequenceError(
    val escapeSequence: String,
    source: Source
) : InvalidUnicodeScalar(
    source = source,
    message = "Unrecognised escape sequence"
)

internal class InvalidUnicodeScalarLiteral(message: String, source: Source) : SourceError(
    message = message,
    source = source
)

fun parse(filename: String, input: String): ModuleNode {
    return parse(
        filename = filename,
        input = input,
        rule = ::parseModule
    )
}

internal fun parseTypesModule(filename: String, input: String): TypesModuleNode {
    return parse(
        filename = filename,
        input = input,
        rule = ::parseTypesModuleTokens
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

private fun parseModule(tokens: TokenIterator<TokenType>): ModuleNode {
    val source = tokens.location()

    val exports = parseExports(tokens)
    val imports = parseImports(tokens)
    val body = parseZeroOrMore(
        parseElement = ::parseModuleStatement,
        isEnd = { tokens -> tokens.isNext(TokenType.END) },
        tokens = tokens
    )
    return ModuleNode(
        exports = exports,
        imports = imports,
        body = body,
        source = source
    )
}

private fun parseExports(tokens: TokenIterator<TokenType>): List<ReferenceNode> {
    if (tokens.trySkip(TokenType.KEYWORD_EXPORT)) {
        val names = parseMany(
            parseElement = {tokens -> parseVariableReference(tokens) },
            parseSeparator = {tokens -> tokens.skip(TokenType.SYMBOL_COMMA)},
            allowZero = false,
            isEnd = {tokens -> tokens.isNext(TokenType.SYMBOL_SEMICOLON)},
            tokens = tokens
        )
        tokens.skip(TokenType.SYMBOL_SEMICOLON)

        return names
    } else {
        return listOf()
    }
}

private fun parseTypesModuleTokens(tokens: TokenIterator<TokenType>): TypesModuleNode {
    val source = tokens.location()

    val imports = parseImports(tokens)
    val body = parseZeroOrMore(
        parseElement = ::parseTypesModuleStatement,
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
        parseElement = ::parseImport,
        isEnd = { tokens -> !tokens.isNext(TokenType.KEYWORD_IMPORT) },
        tokens = tokens
    )
}

internal fun parseTypesModuleStatement(tokens: TokenIterator<TokenType>): TypesModuleStatementNode {
    val token = tokens.peek()

    when {
        token.tokenType == TokenType.KEYWORD_EFFECT ->
            return parseEffectDeclaration(tokens)

        token.tokenType == TokenType.KEYWORD_VAL ->
            return parseValType(tokens)

        else ->
            throw UnexpectedTokenException(
                location = tokens.location(),
                expected = "types module statement",
                actual = token.describe()
            )
    }
}

private fun parseEffectDeclaration(tokens: TokenIterator<TokenType>): EffectDeclarationNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_EFFECT)
    val name = parseIdentifier(tokens)
    val staticParameters = parseStaticParameters(allowVariance = false, tokens = tokens)

    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return EffectDeclarationNode(
        name = name,
        staticParameters = staticParameters,
        source = source
    )
}

private fun parseValType(tokens: TokenIterator<TokenType>): ValTypeNode {
    val source = tokens.location()

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

internal fun parseImport(tokens: TokenIterator<TokenType>): ImportNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_IMPORT)

    val target = parseTarget(tokens)

    tokens.skip(TokenType.KEYWORD_FROM)

    val isLocal = tokens.trySkip(TokenType.SYMBOL_DOT)

    val moduleNameParts = parseMany(
        parseElement = ::parseIdentifier,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_DOT) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_SEMICOLON) },
        tokens = tokens,
        allowZero = false
    )
    tokens.skip(TokenType.SYMBOL_SEMICOLON)
    val path = ImportPath(
        base = if (isLocal) ImportPathBase.Relative else ImportPathBase.Absolute,
        parts = moduleNameParts
    )
    return ImportNode(target = target, path = path, source = source)
}

internal fun parseModuleStatement(tokens: TokenIterator<TokenType>): ModuleStatementNode {
    if (tokens.isNext(TokenType.KEYWORD_EFFECT)) {
        return parseEffectDefinition(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_TYPE)) {
        return parseTypeAlias(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_SHAPE)) {
        return parseShape(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_UNION)) {
        return parseUnion(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_FUN)) {
        return parseFunctionDeclaration(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_VAL)) {
        return parseVal(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_VARARGS)) {
        return parseVarargsDeclaration(tokens)
    } else {
        throw UnexpectedTokenException(
            expected = "module statement",
            actual = tokens.peek().describe(),
            location = tokens.location()
        )
    }
}

private fun parseEffectDefinition(tokens: TokenIterator<TokenType>): EffectDefinitionNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_EFFECT)
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)
    val operations = parseMany(
        parseElement = { tokens ->
            val operationSource = tokens.location()

            tokens.skip(TokenType.SYMBOL_DOT)
            val operationName = parseIdentifier(tokens)
            tokens.skip(TokenType.SYMBOL_COLON)

            val typeSource = tokens.location()
            val parameters = parseFunctionTypeParameters(tokens)
            tokens.skip(TokenType.SYMBOL_ARROW)
            val returnType = parseStaticExpression(tokens)

            OperationDefinitionNode(
                name = operationName,
                type = FunctionTypeNode(
                    staticParameters = listOf(),
                    positionalParameters = parameters.positional,
                    namedParameters = parameters.named,
                    effects = listOf(),
                    returnType = returnType,
                    source = typeSource
                ),
                source = operationSource
            )
        },
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        allowZero = false,
        allowTrailingSeparator = true,
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

    return EffectDefinitionNode(
        name = name,
        operations = operations,
        source = source
    )
}

private fun parseTypeAlias(tokens: TokenIterator<TokenType>): TypeAliasNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_TYPE)
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL_EQUALS)
    val expression = parseStaticExpression(tokens)
    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return TypeAliasNode(
        name = name,
        expression = expression,
        source = source
    )
}

private fun parseShape(tokens: TokenIterator<TokenType>): ShapeNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_SHAPE)
    val name = parseIdentifier(tokens)
    val staticParameters = parseStaticParameters(allowVariance = true, tokens = tokens)

    val extends = if (tokens.trySkip(TokenType.KEYWORD_EXTENDS)) {
        parseMany(
            parseElement = { tokens -> parseStaticExpression(tokens) },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens -> !tokens.isNext(TokenType.SYMBOL_COMMA) },
            allowZero = false,
            tokens = tokens,
            allowTrailingSeparator = false
        )
    } else {
        listOf()
    }

    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)

    val fields = parseShapeFields(tokens)

    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)
    return ShapeNode(
        name = name,
        staticParameters = staticParameters,
        extends = extends,
        fields = fields,
        source = source
    )
}

private fun parseShapeFields(tokens: TokenIterator<TokenType>): List<ShapeFieldNode> {
    return parseMany(
        parseElement = ::parseShapeField,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        allowZero = true,
        tokens = tokens,
        allowTrailingSeparator = true
    )
}

private fun parseShapeField(tokens: TokenIterator<TokenType>): ShapeFieldNode {
    val source = tokens.location()

    val name = parseIdentifier(tokens)

    val shape = if (tokens.trySkip(TokenType.KEYWORD_FROM)) {
        parseStaticExpression(tokens)
    } else {
        null
    }

    val type = if (tokens.trySkip(TokenType.SYMBOL_COLON)) {
        parseStaticExpression(tokens)
    } else {
        null
    }

    val value = if (tokens.trySkip(TokenType.SYMBOL_EQUALS)) {
        parseExpression(tokens)
    } else {
        null
    }

    return ShapeFieldNode(
        shape = shape,
        name = name,
        type = type,
        value = value,
        source = source
    )
}

private fun parseUnion(tokens: TokenIterator<TokenType>): UnionNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_UNION)
    val name = parseIdentifier(tokens)
    val staticParameters = parseStaticParameters(allowVariance = true, tokens = tokens)

    val explicitTag = if (tokens.trySkip(TokenType.SYMBOL_SUBTYPE)) {
         parseVariableReference(tokens)
    } else {
        null
    }

    tokens.skip(TokenType.SYMBOL_EQUALS)
    tokens.trySkip(TokenType.SYMBOL_BAR)

    val members = parseMany(
        parseElement = { tokens -> parseUnionMember(tokens, unionStaticParameters = staticParameters) },
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_BAR) },
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

private fun parseUnionMember(
    tokens: TokenIterator<TokenType>,
    unionStaticParameters: List<StaticParameterNode>
): UnionMemberNode {
    val source = tokens.location()
    val name = parseIdentifier(tokens)

    val staticParameterNames = if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
         val staticParameterNames = parseMany(
            parseElement = ::parseIdentifier,
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
            allowZero = false,
            allowTrailingSeparator = true,
            tokens = tokens
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
        staticParameterNames
    } else {
        listOf()
    }

    val staticParameters = staticParameterNames.map { staticParameterName ->
        unionStaticParameters.find { parameter -> parameter.name == staticParameterName }!!.copy()
    }


    if (tokens.trySkip(TokenType.SYMBOL_OPEN_BRACE)) {
        val fields = parseShapeFields(tokens)
        tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

        return UnionMemberNode(
            name = name,
            staticParameters = staticParameters,
            extends = listOf(),
            fields = fields,
            source = source
        )
    } else {
        return UnionMemberNode(
            name = name,
            staticParameters = staticParameters,
            extends = listOf(),
            fields = listOf(),
            source = source
        )
    }
}

private fun parseVarargsDeclaration(tokens: TokenIterator<TokenType>): VarargsDeclarationNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_VARARGS)

    val name = parseIdentifier(tokens)

    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val cons = parseVariableReference(tokens)
    tokens.skip(TokenType.SYMBOL_COMMA)
    val nil = parseVariableReference(tokens)
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return VarargsDeclarationNode(
        name = name,
        cons = cons,
        nil = nil,
        source = source
    )
}

internal fun parseFunctionDeclaration(tokens: TokenIterator<TokenType>): FunctionDeclarationNode {
    val source = tokens.location()

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
            body = body,
            inferReturnType = false,
            source = source
        )
    }
}

private data class FunctionSignature(
    val staticParameters: List<StaticParameterNode>,
    val parameters: List<ParameterNode>,
    val namedParameters: List<ParameterNode>,
    val effects: List<StaticExpressionNode>,
    val returnType: StaticExpressionNode?
)

private fun parseFunctionSignature(tokens: TokenIterator<TokenType>): FunctionSignature {
    val staticParameters = parseStaticParameters(allowVariance = false, tokens = tokens)

    val parameters = parseParameters(tokens)

    // TODO: allow trailing commas?
    val effects = parseEffects(tokens)

    val returnType = if (tokens.trySkip(TokenType.SYMBOL_ARROW)) {
        parseStaticExpression(tokens)
    } else {
        null
    }

    return FunctionSignature(
        staticParameters = staticParameters,
        parameters = parameters.positional,
        namedParameters = parameters.named,
        effects = effects,
        returnType = returnType
    )
}

class Parameters(val positional: List<ParameterNode>, val named: List<ParameterNode>)

private fun parseParameters(tokens: TokenIterator<TokenType>): Parameters {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val parameters = parseZeroOrMore(
        parseElement = ::parseParametersPart,
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        tokens = tokens,
        allowTrailingSeparator = true
    )

    // TODO: don't allow positional parameter after named
    val positionalParameters = mutableListOf<ParameterNode>()
    val namedParameters = mutableListOf<ParameterNode>()
    for (parameter in parameters) {
        when (parameter) {
            is Parameter.Positional -> positionalParameters.add(parameter.node)
            is Parameter.Named -> namedParameters.add(parameter.node)
        }
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    return Parameters(positional = positionalParameters, named = namedParameters)
}

internal fun parseStaticParameters(
    allowVariance: Boolean,
    tokens: TokenIterator<TokenType>
): List<StaticParameterNode> {
    return if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
        val typeParameters = parseMany(
            parseElement = { tokens -> parseStaticParameter(tokens, allowVariance = allowVariance) },
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

private fun parseEffects(tokens: TokenIterator<TokenType>): List<StaticExpressionNode> {
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

private fun parseEffect(tokens: TokenIterator<TokenType>): StaticExpressionNode {
    tokens.skip(TokenType.SYMBOL_BANG)
    return parseStaticExpression(tokens)
}

private fun parseFunctionStatements(tokens: TokenIterator<TokenType>): Block {
    val source = tokens.location()

    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)

    val statements = mutableListOf<FunctionStatementNode>()
    var lastStatement: FunctionStatementNode? = null

    while (!(tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) || (lastStatement != null && lastStatement.isReturn))) {
        lastStatement = parseFunctionStatement(tokens)
        statements.add(lastStatement)
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)
    return Block(
        statements = statements,
        source = source
    )
}

private fun parseStaticParameter(tokens: TokenIterator<TokenType>, allowVariance: Boolean): StaticParameterNode {
    val source = tokens.location()

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

        return TypeParameterNode(
            name = name,
            variance = variance,
            source = source
        )
    }
}

private sealed class Parameter {
    class Positional(val node: ParameterNode): Parameter()
    class Named(val node: ParameterNode): Parameter()
}

private fun parseParametersPart(tokens: TokenIterator<TokenType>) : Parameter {
    if (tokens.isNext(TokenType.SYMBOL_DOT)) {
        return Parameter.Named(parseNamedParameter(tokens))
    } else {
        return Parameter.Positional(parsePositionalParameter(tokens))
    }
}

private fun parsePositionalParameter(tokens: TokenIterator<TokenType>): ParameterNode {
    val source = tokens.location()

    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return ParameterNode(name, type, source)
}

private fun parseNamedParameter(tokens: TokenIterator<TokenType>): ParameterNode {
    val source = tokens.location()

    tokens.skip(TokenType.SYMBOL_DOT)
    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return ParameterNode(name, type, source)
}

private fun parseTypeSpec(tokens: TokenIterator<TokenType>): StaticExpressionNode {
    tokens.skip(TokenType.SYMBOL_COLON)
    return parseStaticExpression(tokens)
}

internal fun parseFunctionStatement(tokens: TokenIterator<TokenType>) : FunctionStatementNode {
    val token = tokens.peek()

    if (token.tokenType == TokenType.KEYWORD_VAL) {
        return parseVal(tokens)
    } else if (token.tokenType == TokenType.KEYWORD_FUN && tokens.peek(1).tokenType == TokenType.IDENTIFIER) {
        return parseFunctionDeclaration(tokens)
    } else if (token.tokenType == TokenType.KEYWORD_TAILREC) {
        val source = tokens.location()
        tokens.skip()
        val expression = parseExpression(tokens)
        return ExpressionStatementNode(
            type = ExpressionStatementNode.Type.TAILREC_RETURN,
            expression = expression,
            source = source
        )
    } else {
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
            val type = if (isReturn) {
                ExpressionStatementNode.Type.RETURN
            } else {
                ExpressionStatementNode.Type.NO_RETURN
            }
            return ExpressionStatementNode(
                expression,
                type = type,
                source = expression.source
            )
        }
    }
}

private fun branchesReturn(expression: Node, branches: Iterable<Block>): Boolean {
    val isReturns = branches.map { body ->
        body.statements.any(FunctionStatementNode::isReturn)
    }.toSet()
    return if (isReturns.size == 1) {
        isReturns.single()
    } else {
        // TODO: raise a more specific exception
        // TODO: should these checks be in the expression nodes themselves?
        throw SourceError("Some branches do not provide a value", source = expression.source)
    }
}

private fun parseIf(tokens: TokenIterator<TokenType>) : IfNode {
    val source = tokens.location()

    val conditionalBranches = parseConditionalBranches(tokens, source)

    val elseBranch = if (tokens.trySkip(TokenType.KEYWORD_ELSE)) {
        parseFunctionStatements(tokens)
    } else {
        val elseSource = tokens.location()
        Block(statements = listOf(), source = elseSource)
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
        val branch = tryParseAdditionalConditionalBranch(tokens)
        if (branch == null) {
            return branches
        } else {
            branches.add(branch)
        }
    }
}

private fun tryParseAdditionalConditionalBranch(
    tokens: TokenIterator<TokenType>
): ConditionalBranchNode? {
    val source = tokens.location()

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

private fun parseWhen(tokens: TokenIterator<TokenType>): WhenNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_WHEN)

    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val expression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)
    val branches = parseMany(
        parseElement = ::parseWhenBranch,
        isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) || tokens.isNext(TokenType.KEYWORD_ELSE) },
        tokens = tokens,
        allowZero = true
    )

    val elseBranch = if (tokens.trySkip(TokenType.KEYWORD_ELSE)) {
        parseFunctionStatements(tokens)
    } else {
        null
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

    return WhenNode(
        expression = expression,
        branches = branches,
        elseBranch = elseBranch,
        source = source
    )
}

private fun parseWhenBranch(tokens: TokenIterator<TokenType>): WhenBranchNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_IS)
    val type = parseStaticExpression(tokens)
    val body = parseFunctionStatements(tokens)
    return WhenBranchNode(
        type = type,
        body = body,
        source = source
    )
}

private fun parseVal(tokens: TokenIterator<TokenType>): ValNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_VAL)
    val target = parseTarget(tokens)
    tokens.skip(TokenType.SYMBOL_EQUALS)
    val expression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return ValNode(
        target = target,
        expression = expression,
        source = source
    )
}

internal fun parseTarget(tokens: TokenIterator<TokenType>): TargetNode {
    val source = tokens.location()

    if (tokens.trySkip(TokenType.SYMBOL_HASH)) {
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        val elements = parseMany(
            parseElement = { tokens -> parseTarget(tokens) },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
            allowTrailingSeparator = true,
            allowZero = false,
            tokens = tokens
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

        return TargetNode.Tuple(
            elements = elements,
            source = source
        )
    } else if (tokens.trySkip(TokenType.SYMBOL_AT)) {
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        val fields = parseMany(
            parseElement = { tokens ->
                tokens.skip(TokenType.SYMBOL_DOT)
                val fieldName = parseFieldName(tokens)
                tokens.skip(TokenType.KEYWORD_AS)
                val target = parseTarget(tokens)
                fieldName to target
            },
            parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
            allowTrailingSeparator = true,
            allowZero = false,
            tokens = tokens
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

        return TargetNode.Fields(
            fields = fields,
            source = source
        )
    } else {
        val name = parseIdentifier(tokens)

        return TargetNode.Variable(
            name = name,
            source = source
        )
    }
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
    val primaryExpression = tryParsePrimaryExpression(tokens)
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
            left = operationParser.parse(left, tokens)
        }
    }
}

private val PIPELINE_PRECEDENCE = 4
private val OR_PRECEDENCE = 6
private val AND_PRECEDENCE = 7
private val COMPARISON_PRECEDENCE = 8
private val IS_PRECEDENCE = 9
private val ADD_PRECENDECE = 11
private val SUBTRACT_PRECEDENCE = 11
private val MULTIPLY_PRECEDENCE = 12
private val UNARY_PRECEDENCE = 13
private val CALL_PRECEDENCE = 14
private val FIELD_ACCESS_PRECEDENCE = 14

private interface OperationParser: ExpressionParser<ExpressionNode> {
    companion object {
        val parsers = listOf(
            InfixOperationParser(BinaryOperator.EQUALS, TokenType.SYMBOL_DOUBLE_EQUALS, COMPARISON_PRECEDENCE),
            InfixOperationParser(BinaryOperator.NOT_EQUAL, TokenType.SYMBOL_NOT_EQUAL, COMPARISON_PRECEDENCE),
            InfixOperationParser(BinaryOperator.LESS_THAN, TokenType.SYMBOL_LESS_THAN, COMPARISON_PRECEDENCE),
            InfixOperationParser(BinaryOperator.LESS_THAN_OR_EQUAL, TokenType.SYMBOL_LESS_THAN_OR_EQUAL, COMPARISON_PRECEDENCE),
            InfixOperationParser(BinaryOperator.GREATER_THAN, TokenType.SYMBOL_GREATER_THAN, COMPARISON_PRECEDENCE),
            InfixOperationParser(BinaryOperator.GREATER_THAN_OR_EQUAL, TokenType.SYMBOL_GREATER_THAN_OR_EQUAL, COMPARISON_PRECEDENCE),
            InfixOperationParser(BinaryOperator.ADD, TokenType.SYMBOL_PLUS, ADD_PRECENDECE),
            InfixOperationParser(BinaryOperator.SUBTRACT, TokenType.SYMBOL_MINUS, SUBTRACT_PRECEDENCE),
            InfixOperationParser(BinaryOperator.MULTIPLY, TokenType.SYMBOL_ASTERISK, MULTIPLY_PRECEDENCE),
            InfixOperationParser(BinaryOperator.AND, TokenType.SYMBOL_DOUBLE_AMPERSAND, AND_PRECEDENCE),
            InfixOperationParser(BinaryOperator.OR, TokenType.SYMBOL_DOUBLE_VERTICAL_BAR, OR_PRECEDENCE),
            CallParser(TokenType.SYMBOL_BANG),
            CallParser(TokenType.SYMBOL_OPEN_SQUARE_BRACKET),
            CallParser(TokenType.SYMBOL_OPEN_PAREN),
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
    private val operator: BinaryOperator,
    override val operatorToken: TokenType,
    override val precedence: Int
) : OperationParser {
    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        tokens.skip()
        val right = parseExpression(tokens, precedence + 1)
        return BinaryOperationNode(operator, left, right, left.source)
    }
}

private class CallParser(override val operatorToken: TokenType) : OperationParser {
    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val hasEffect = tokens.trySkip(TokenType.SYMBOL_BANG)

        val typeArguments = if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
            val typeArguments = parseMany(
                parseElement = { tokens -> parseStaticExpression(tokens) },
                parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
                isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
                allowZero = false,
                tokens = tokens
            )
            tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
            typeArguments
        } else {
            listOf()
        }

        val (positionalArguments, namedArguments) = parseCallArguments(tokens)
        return CallNode(
            receiver = left,
            staticArguments = typeArguments,
            positionalArguments = positionalArguments,
            namedArguments = namedArguments,
            hasEffect = hasEffect,
            source = left.source
        )
    }

    override val precedence: Int
        get() = CALL_PRECEDENCE
}

private object PartialCallParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_TILDE

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val operatorSource = tokens.location()
        tokens.skip()
        val (positionalArguments, namedArguments) = parseCallArguments(tokens)
        return PartialCallNode(
            receiver = left,
            staticArguments = listOf(),
            positionalArguments = positionalArguments,
            namedArguments = namedArguments,
            source = operatorSource
        )
    }

    override val precedence: Int
        get() = CALL_PRECEDENCE
}

private fun parseCallArguments(tokens: TokenIterator<TokenType>): Pair<List<ExpressionNode>, List<CallNamedArgumentNode>> {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val arguments = parseMany(
        parseElement = ::parseArgument,
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
                    throw ParseError("Positional argument cannot appear after named argument", source = argument.expression.source)
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

private fun parseArgument(tokens: TokenIterator<TokenType>): ParsedArgument {
    val source = tokens.location()

    if (tokens.trySkip(TokenType.SYMBOL_DOT)) {
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
        val operatorSource = tokens.location()
        tokens.skip()
        val right = parseExpression(tokens, precedence = precedence + 1)
        return CallNode(
            receiver = right,
            positionalArguments = listOf(left),
            namedArguments = listOf(),
            staticArguments = listOf(),
            hasEffect = false,
            source = operatorSource
        )
    }
}

private object FieldAccessParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_DOT

    override val precedence: Int
        get() = FIELD_ACCESS_PRECEDENCE

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        val operatorSource = tokens.location()
        tokens.skip()
        val fieldName = parseFieldName(tokens)
        return FieldAccessNode(
            receiver = left,
            fieldName = fieldName,
            source = operatorSource
        )
    }
}

private fun parseFieldName(tokens: TokenIterator<TokenType>): FieldNameNode {
    val source = tokens.location()
    val identifier = parseIdentifier(tokens)
    return FieldNameNode(identifier, source = source)
}


private object IsParser : OperationParser {
    override val operatorToken: TokenType
        get() = TokenType.KEYWORD_IS

    override val precedence: Int
        get() = IS_PRECEDENCE

    override fun parse(left: ExpressionNode, tokens: TokenIterator<TokenType>): ExpressionNode {
        tokens.skip()
        val type = parseStaticExpression(tokens)
        return IsNode(
            expression = left,
            type = type,
            source = left.source
        )
    }
}

internal fun tryParsePrimaryExpression(tokens: TokenIterator<TokenType>) : ExpressionNode? {
    val source = tokens.location()

    val tokenType = tokens.peek().tokenType;
    when (tokenType) {
        TokenType.KEYWORD_UNIT -> {
            tokens.skip()
            return UnitLiteralNode(source)
        }
        TokenType.INTEGER -> {
            val token = tokens.next()
            return IntegerLiteralNode(token.value.toBigInteger(), source)
        }
        TokenType.IDENTIFIER -> {
            return parseVariableReference(tokens)
        }
        TokenType.KEYWORD_TRUE -> {
            tokens.skip()
            return BooleanLiteralNode(true, source)
        }
        TokenType.KEYWORD_FALSE -> {
            tokens.skip()
            return BooleanLiteralNode(false, source)
        }
        TokenType.SYMBOL_HASH -> {
            tokens.skip()
            tokens.skip(TokenType.SYMBOL_OPEN_PAREN)

            val elements = parseMany(
                parseElement = { tokens -> parseExpression(tokens) },
                parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
                isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
                allowZero = true,
                allowTrailingSeparator = true,
                tokens = tokens
            )

            tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
            return TupleNode(
                elements = elements,
                source = source
            )
        }
        TokenType.STRING -> {
            val token = tokens.next()
            val value = decodeUnicodeScalarToken(token.value, source = source)
            return StringLiteralNode(value, source)
        }
        TokenType.CODE_POINT -> {
            val token = tokens.next()
            val stringValue = decodeUnicodeScalarToken(token.value, source = source)
            if (stringValue.codePointCount(0, stringValue.length) == 1) {
                val value = stringValue.codePointAt(0)
                return UnicodeScalarLiteralNode(value, source)
            } else {
                throw InvalidUnicodeScalarLiteral("Unicode scalar literal has ${stringValue.length} Unicode scalars", source = source)
            }
        }
        TokenType.SYMBOL_OPEN_PAREN -> {
            tokens.skip()
            val expression = parseExpression(tokens)
            tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
            return expression
        }
        TokenType.KEYWORD_IF -> {
            return parseIf(tokens)
        }
        TokenType.KEYWORD_WHEN -> {
            return parseWhen(tokens)
        }
        TokenType.KEYWORD_FUN -> {
            tokens.skip()
            val signature = parseFunctionSignature(tokens)

            val (body, inferReturnType) = if (tokens.trySkip(TokenType.SYMBOL_FAT_ARROW)) {
                val expression = parseExpression(tokens)
                val statement = ExpressionStatementNode(
                    expression = expression,
                    type = ExpressionStatementNode.Type.RETURN,
                    source = expression.source
                )
                val body = Block(
                    statements = listOf(statement),
                    source = expression.source
                )
                Pair(body, true)
            } else {
                Pair(parseFunctionStatements(tokens), false)
            }

            return FunctionExpressionNode(
                staticParameters = signature.staticParameters,
                parameters = signature.parameters,
                namedParameters = signature.namedParameters,
                returnType = signature.returnType,
                effects = signature.effects,
                body = body,
                inferReturnType = inferReturnType,
                source = source
            )
        }
        TokenType.KEYWORD_NOT -> {
            tokens.skip()
            val operand = parseExpression(tokens, precedence = UNARY_PRECEDENCE)
            return UnaryOperationNode(
                operator = UnaryOperator.NOT,
                operand = operand,
                source = source
            )
        }
        TokenType.SYMBOL_MINUS -> {
            tokens.skip()
            val operand = parseExpression(tokens, precedence = UNARY_PRECEDENCE)
            return UnaryOperationNode(
                operator = UnaryOperator.MINUS,
                operand = operand,
                source = source
            )
        }
        TokenType.KEYWORD_HANDLE -> {
            return parseHandle(tokens, source)
        }
        else -> return null
    }
}

private fun parseHandle(tokens: TokenIterator<TokenType>, source: StringSource): HandleNode {
    tokens.skip()

    val effect = parseStaticExpression(tokens)
    val body = parseFunctionStatements(tokens)
    tokens.skip(TokenType.KEYWORD_ON)
    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)
    val handlers = parseMany(
        parseElement = { tokens ->
            tokens.skip(TokenType.SYMBOL_DOT)
            val operationName = parseIdentifier(tokens)
            tokens.skip(TokenType.SYMBOL_EQUALS)

            val handlerSource = tokens.location()
            val handlerParameters = parseParameters(tokens)
            val handlerBody = parseHandlerBody(tokens)
            operationName to FunctionExpressionNode(
                staticParameters = listOf(),
                parameters = handlerParameters.positional,
                namedParameters = handlerParameters.named,
                effects = listOf(),
                returnType = null,
                inferReturnType = true,
                body = handlerBody,
                source = handlerSource
            )
        },
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        allowTrailingSeparator = true,
        allowZero = false,
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

    return HandleNode(
        effect = effect,
        body = body,
        handlers = handlers,
        source = source
    )
}

private fun parseHandlerBody(tokens: TokenIterator<TokenType>): Block {
    val source = tokens.location()
    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)
    val statements = parseMany(
        parseElement = { tokens ->
            val statement = parseFunctionStatement(tokens)
            if (statement.isReturn) {
                throw ParseError("cannot return from a handler without explicit exit", source = statement.source)
            } else {
                statement
            }
        },
        isEnd = { tokens -> tokens.isNext(TokenType.KEYWORD_EXIT) },
        allowZero = true,
        tokens = tokens
    )
    tokens.skip(TokenType.KEYWORD_EXIT)
    val exitExpression = parseExpression(tokens)
    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)
    return Block(
        statements = statements + listOf(
            ExpressionStatementNode(
                expression = exitExpression,
                type = ExpressionStatementNode.Type.RETURN,
                source = exitExpression.source
            )
        ),
        source = source
    )
}

private fun parseVariableReference(tokens: TokenIterator<TokenType>): ReferenceNode {
    val source = tokens.location()
    val value = parseIdentifier(tokens)
    return ReferenceNode(value, source)
}

private fun decodeUnicodeScalarToken(value: String, source: StringSource): String {
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
                throw InvalidUnicodeScalar(
                    source = source.at(matcher.end() + 1),
                    message = "Expected opening brace"
                )
            }
            val startIndex = matcher.end() + 1
            val endIndex = value.indexOf("}", startIndex = startIndex)
            if (endIndex == -1) {
                throw InvalidUnicodeScalar(
                    source = source.at(matcher.end() + 1),
                    message = "Could not find closing brace"
                )
            }
            val hex = value.subSequence(startIndex, endIndex).toString()
            val unicodeScalar = hex.toInt(16)
            decoded.appendCodePoint(unicodeScalar)
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

internal fun parseStaticExpression(tokens: TokenIterator<TokenType>) : StaticExpressionNode {
    return parseStaticExpression(tokens = tokens, precedence = Int.MIN_VALUE)
}

private fun parseStaticExpression(
    tokens: TokenIterator<TokenType>,
    precedence: Int
) : StaticExpressionNode {
    var left: StaticExpressionNode  = parsePrimaryStaticExpression(tokens)
    while (true) {
        val next = tokens.peek()
        val operationParser = StaticOperationParser.lookup(next.tokenType)
        if (operationParser == null || operationParser.precedence < precedence) {
            return left
        } else {
            left = operationParser.parse(left, tokens)
        }
    }
}

private fun parsePrimaryStaticExpression(
    tokens: TokenIterator<TokenType>
): StaticExpressionNode {
    if (tokens.isNext(TokenType.KEYWORD_FUN_CAPITAL)) {
        return parseFunctionType(tokens)
    } else if (tokens.isNext(TokenType.SYMBOL_HASH)) {
        return parseTupleType(tokens)
    } else {
        return parseVariableReference(tokens)
    }
}

private fun parseFunctionType(tokens: TokenIterator<TokenType>): StaticExpressionNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_FUN_CAPITAL)
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

private fun parseTupleType(tokens: TokenIterator<TokenType>): StaticExpressionNode {
    val source = tokens.location()

    tokens.skip(TokenType.SYMBOL_HASH)
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)

    val elementTypes = parseMany(
        parseElement = { tokens -> parseStaticExpression(tokens) },
        parseSeparator = { tokens -> tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens -> tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        allowTrailingSeparator = true,
        allowZero = true,
        tokens = tokens
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    return TupleTypeNode(
        elementTypes = elementTypes,
        source = source
    )
}

private class FunctionTypeParameters(
    val positional: List<StaticExpressionNode>,
    val named: List<ParameterNode>
)

private fun parseFunctionTypeParameters(tokens: TokenIterator<TokenType>): FunctionTypeParameters {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)

    val positionalParameters = mutableListOf<StaticExpressionNode>()
    val namedParameters = mutableListOf<ParameterNode>()

    while (true) {
        if (tokens.trySkip(TokenType.SYMBOL_CLOSE_PAREN)) {
            return FunctionTypeParameters(
                positional = positionalParameters,
                named = namedParameters
            )
        }

        if (tokens.isNext(TokenType.SYMBOL_DOT)) {
            val parameter = parseNamedParameter(tokens)
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

private interface StaticOperationParser: ExpressionParser<StaticExpressionNode> {
    companion object {
        private val parsers = listOf(
            StaticCallParser,
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

private object StaticCallParser : StaticOperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_OPEN_SQUARE_BRACKET

    override fun parse(left: StaticExpressionNode, tokens: TokenIterator<TokenType>): StaticExpressionNode {
        tokens.skip()
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

    override fun parse(left: StaticExpressionNode, tokens: TokenIterator<TokenType>): StaticExpressionNode {
        tokens.skip()
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
