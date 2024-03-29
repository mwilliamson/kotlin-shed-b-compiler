package org.shedlang.compiler.frontend.parser

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.MissingReturnTypeError
import org.shedlang.compiler.types.Variance
import java.nio.CharBuffer
import java.util.regex.Pattern

internal fun parse(filename: String, input: String): ModuleNode {
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
        parseElement = { parseModuleStatement(tokens) },
        isEnd = { tokens.isNext(TokenType.END) },
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
            parseElement = { parseVariableReference(tokens) },
            parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
            allowZero = false,
            isEnd = { tokens.isNext(TokenType.SYMBOL_SEMICOLON) },
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
        parseElement = { parseTypesModuleStatement(tokens) },
        isEnd = { tokens.isNext(TokenType.END) },
    )

    return TypesModuleNode(
        imports = imports,
        body = body,
        source = source
    )
}

private fun parseImports(tokens: TokenIterator<TokenType>): List<ImportNode> {
    return parseZeroOrMore(
        parseElement = { parseImport(tokens) },
        isEnd = { !tokens.isNext(TokenType.KEYWORD_IMPORT) },
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
            throw UnexpectedTokenError(
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

    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return EffectDeclarationNode(
        name = name,
        source = source
    )
}

private fun parseValType(tokens: TokenIterator<TokenType>): ValTypeNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_VAL)
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL_COLON)
    val type = parseTypeLevelExpression(tokens)
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
        parseElement = { parseIdentifier(tokens) },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_DOT) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_SEMICOLON) },
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
        return parseFunctionDefinition(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_VAL)) {
        return parseVal(tokens)
    } else if (tokens.isNext(TokenType.KEYWORD_VARARGS)) {
        return parseVarargsDeclaration(tokens)
    } else {
        throw UnexpectedTokenError(
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
        parseElement = {
            val operationSource = tokens.location()

            tokens.skip(TokenType.SYMBOL_DOT)
            val operationName = parseIdentifier(tokens)
            tokens.skip(TokenType.SYMBOL_COLON)

            val typeSource = tokens.location()
            val parameters = parseFunctionTypeParameters(tokens)
            tokens.skip(TokenType.SYMBOL_ARROW)
            val returnType = parseTypeLevelExpression(tokens)

            OperationDefinitionNode(
                name = operationName,
                type = FunctionTypeNode(
                    typeLevelParameters = listOf(),
                    positionalParameters = parameters.positional,
                    namedParameters = parameters.named,
                    effect = null,
                    returnType = returnType,
                    source = typeSource
                ),
                source = operationSource
            )
        },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        allowZero = false,
        allowTrailingSeparator = true,
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

    return EffectDefinitionNode(
        name = name,
        operations = operations.sortedBy { operation -> operation.name },
        source = source
    )
}

private fun parseTypeAlias(tokens: TokenIterator<TokenType>): TypeAliasNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_TYPE)
    val name = parseIdentifier(tokens)
    tokens.skip(TokenType.SYMBOL_EQUALS)
    val expression = parseTypeLevelExpression(tokens)
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
    val typeLevelParameters = parseTypeLevelParameters(allowVariance = true, tokens = tokens)

    val extends = if (tokens.trySkip(TokenType.KEYWORD_EXTENDS)) {
        parseMany(
            parseElement = { parseTypeLevelExpression(tokens) },
            parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { !tokens.isNext(TokenType.SYMBOL_COMMA) },
            allowZero = false,
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
        typeLevelParameters = typeLevelParameters,
        extends = extends,
        fields = fields,
        source = source
    )
}

private fun parseShapeFields(tokens: TokenIterator<TokenType>): List<ShapeFieldNode> {
    return parseMany(
        parseElement = { parseShapeField(tokens) },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        allowZero = true,
        allowTrailingSeparator = true
    )
}

private fun parseShapeField(tokens: TokenIterator<TokenType>): ShapeFieldNode {
    val source = tokens.location()

    val name = parseIdentifier(tokens)

    val shape = if (tokens.trySkip(TokenType.KEYWORD_FROM)) {
        parseTypeLevelExpression(tokens)
    } else {
        null
    }

    tokens.skip(TokenType.SYMBOL_COLON)
    val type = parseTypeLevelExpression(tokens)

    return ShapeFieldNode(
        shape = shape,
        name = name,
        type = type,
        source = source
    )
}

private fun parseUnion(tokens: TokenIterator<TokenType>): UnionNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_UNION)
    val name = parseIdentifier(tokens)
    val typeLevelParameters = parseTypeLevelParameters(allowVariance = true, tokens = tokens)

    val explicitTag = if (tokens.trySkip(TokenType.SYMBOL_SUBTYPE)) {
         parseVariableReference(tokens)
    } else {
        null
    }

    tokens.skip(TokenType.SYMBOL_EQUALS)
    tokens.trySkip(TokenType.SYMBOL_BAR)

    val members = parseMany(
        parseElement = { parseUnionMember(tokens, unionTypeLevelParameters = typeLevelParameters) },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_BAR) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_SEMICOLON) },
        allowZero = false,
    )

    tokens.skip(TokenType.SYMBOL_SEMICOLON)

    return UnionNode(
        name = name,
        typeLevelParameters = typeLevelParameters,
        superType = explicitTag,
        members = members,
        source = source
    )
}

private fun parseUnionMember(
    tokens: TokenIterator<TokenType>,
    unionTypeLevelParameters: List<TypeLevelParameterNode>
): UnionMemberNode {
    val source = tokens.location()
    val name = parseIdentifier(tokens)

    val typeLevelParameterNames = if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
         val typeLevelParameterNames = parseMany(
            parseElement = { parseIdentifier(tokens) },
            parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
            allowZero = false,
            allowTrailingSeparator = true,
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
        typeLevelParameterNames
    } else {
        listOf()
    }

    val typeLevelParameters = typeLevelParameterNames.map { typeLevelParameterName ->
        unionTypeLevelParameters.find { parameter -> parameter.name == typeLevelParameterName }!!.copy()
    }


    if (tokens.trySkip(TokenType.SYMBOL_OPEN_BRACE)) {
        val fields = parseShapeFields(tokens)
        tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

        return UnionMemberNode(
            name = name,
            typeLevelParameters = typeLevelParameters,
            extends = listOf(),
            fields = fields,
            source = source
        )
    } else {
        return UnionMemberNode(
            name = name,
            typeLevelParameters = typeLevelParameters,
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

internal fun parseFunctionDefinition(tokens: TokenIterator<TokenType>): FunctionDefinitionNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_FUN)
    val name = parseIdentifier(tokens)
    val signature = parseFunctionDefinitionSignature(tokens)
    val body = parseFunctionStatements(tokens)

    if (signature.returnType == null) {
        throw MissingReturnTypeError("Function declaration must have return type", source = source)
    } else {
        return FunctionDefinitionNode(
            name = name,
            typeLevelParameters = signature.typeLevelParameters,
            parameters = signature.parameters,
            namedParameters = signature.namedParameters,
            returnType = signature.returnType,
            effect = signature.effect,
            body = body,
            inferReturnType = false,
            source = source
        )
    }
}

private data class FunctionSignature(
    val typeLevelParameters: List<TypeLevelParameterNode>,
    val parameters: List<ParameterNode>,
    val namedParameters: List<ParameterNode>,
    val effect: FunctionEffectNode?,
    val returnType: TypeLevelExpressionNode?
)

private fun parseFunctionDefinitionSignature(tokens: TokenIterator<TokenType>): FunctionSignature {
    val typeLevelParameters = parseTypeLevelParameters(allowVariance = false, tokens = tokens)

    val parameters = parseParameters(tokens)

    val effect = parseFunctionDefinitionEffect(tokens)

    val returnType = if (tokens.trySkip(TokenType.SYMBOL_ARROW)) {
        parseTypeLevelExpression(tokens)
    } else {
        null
    }

    return FunctionSignature(
        typeLevelParameters = typeLevelParameters,
        parameters = parameters.positional,
        namedParameters = parameters.named,
        effect = effect,
        returnType = returnType
    )
}

class Parameters(val positional: List<ParameterNode>, val named: List<ParameterNode>)

private fun parseParameters(tokens: TokenIterator<TokenType>): Parameters {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val parameters = parseZeroOrMore(
        parseElement = { parseParametersPart(tokens) },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        allowTrailingSeparator = true
    )

    val positionalParameters = mutableListOf<ParameterNode>()
    val namedParameters = mutableListOf<ParameterNode>()
    for (parameter in parameters) {
        when (parameter) {
            is Parameter.Positional ->
                if (namedParameters.isEmpty()) {
                    positionalParameters.add(parameter.node)
                } else {
                    throw PositionalParameterAfterNamedParameterError(source = parameter.node.source)
                }

            is Parameter.Named ->
                namedParameters.add(parameter.node)
        }
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    return Parameters(positional = positionalParameters, named = namedParameters)
}

internal fun parseTypeLevelParameters(
    allowVariance: Boolean,
    tokens: TokenIterator<TokenType>
): List<TypeLevelParameterNode> {
    return if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
        val typeParameters = parseMany(
            parseElement = { parseTypeLevelParameter(tokens, allowVariance = allowVariance) },
            parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
            allowZero = false,
            allowTrailingSeparator = true,
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
        typeParameters
    } else {
        listOf()
    }
}

private fun parseFunctionDefinitionEffect(tokens: TokenIterator<TokenType>): FunctionEffectNode? {
    val source = tokens.location()

    if (tokens.trySkip(TokenType.SYMBOL_BANG)) {
        if (tokens.trySkip(TokenType.SYMBOL_UNDERSCORE)) {
            return FunctionEffectNode.Infer(source = source)
        } else {
            val expression = parseTypeLevelExpression(tokens)
            return FunctionEffectNode.Explicit(expression, source = source)
        }
    } else {
        return null
    }
}

private fun parseFunctionTypeEffect(tokens: TokenIterator<TokenType>): TypeLevelExpressionNode? {
    if (tokens.trySkip(TokenType.SYMBOL_BANG)) {
        return parseTypeLevelExpression(tokens)
    } else {
        return null
    }
}

private fun parseFunctionStatements(tokens: TokenIterator<TokenType>): BlockNode {
    val source = tokens.location()

    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)

    val statements = mutableListOf<FunctionStatementNode>()
    var lastStatement: FunctionStatementNode? = null

    while (!(tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) || (lastStatement != null && lastStatement.terminatesBlock))) {
        lastStatement = parseFunctionStatement(tokens)
        statements.add(lastStatement)
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)
    return BlockNode(
        statements = statements,
        source = source
    )
}

private fun parseTypeLevelParameter(tokens: TokenIterator<TokenType>, allowVariance: Boolean): TypeLevelParameterNode {
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
    val type = tryParseTypeSpec(tokens)
    return ParameterNode(name, type, source)
}

private fun parseNamedParameter(tokens: TokenIterator<TokenType>): ParameterNode {
    val source = tokens.location()

    tokens.skip(TokenType.SYMBOL_DOT)
    val name = parseIdentifier(tokens)
    val type = tryParseTypeSpec(tokens)
    return ParameterNode(name, type, source)
}

private fun tryParseTypeSpec(tokens: TokenIterator<TokenType>): TypeLevelExpressionNode? {
    if (tokens.isNext(TokenType.SYMBOL_COLON)) {
        return parseTypeSpec(tokens)
    } else {
        return null
    }
}

private fun parseTypeSpec(tokens: TokenIterator<TokenType>): TypeLevelExpressionNode {
    tokens.skip(TokenType.SYMBOL_COLON)
    return parseTypeLevelExpression(tokens)
}

internal fun parseFunctionStatement(tokens: TokenIterator<TokenType>) : FunctionStatementNode {
    val token = tokens.peek()

    if (token.tokenType == TokenType.KEYWORD_VAL) {
        return parseVal(tokens)
    } else if (token.tokenType == TokenType.KEYWORD_FUN && tokens.peek(1).tokenType == TokenType.IDENTIFIER) {
        return parseFunctionDefinition(tokens)
    } else if (token.tokenType == TokenType.KEYWORD_SHAPE) {
        return parseShape(tokens)
    } else if (token.tokenType == TokenType.KEYWORD_EFFECT) {
        return parseEffectDefinition(tokens)
    } else if (token.tokenType == TokenType.KEYWORD_TAILREC) {
        val source = tokens.location()
        tokens.skip()
        val expression = parseExpression(tokens)
        return ExpressionStatementNode(
            type = ExpressionStatementNode.Type.TAILREC,
            expression = expression,
            source = source
        )
    } else if (token.tokenType == TokenType.KEYWORD_EXIT) {
        val source = tokens.location()
        tokens.skip()
        val expression = parseExpression(tokens)
        return ExpressionStatementNode(
            type = ExpressionStatementNode.Type.EXIT,
            expression = expression,
            source = source
        )
    } else if (token.tokenType == TokenType.KEYWORD_RESUME) {
        val source = tokens.location()
        tokens.skip()

        val expression = parseExpression(tokens)

        val newState = if (tokens.trySkip(TokenType.KEYWORD_WITH_STATE)) {
            parseExpression(tokens)
        } else {
            null
        }

        return ResumeNode(
            expression = expression,
            newState = newState,
            source = source
        )
    } else {
        val expression = tryParseExpression(tokens)
        if (expression == null) {
            throw UnexpectedTokenError(
                location = tokens.location(),
                expected = "function statement",
                actual = token.describe()
            )
        } else {
            val branches = expressionBranches(expression)
            val isReturn = if (branches == null) {
                !tokens.trySkip(TokenType.SYMBOL_SEMICOLON)
            } else {
                isTerminatingExpression(expression)
            }
            val type = if (isReturn) {
                ExpressionStatementNode.Type.VALUE
            } else {
                ExpressionStatementNode.Type.NO_VALUE
            }
            return ExpressionStatementNode(
                expression,
                type = type,
                source = expression.source
            )
        }
    }
}

private fun parseIf(tokens: TokenIterator<TokenType>) : IfNode {
    val source = tokens.location()

    val conditionalBranches = parseConditionalBranches(tokens, source)

    val elseBranch = if (tokens.trySkip(TokenType.KEYWORD_ELSE)) {
        parseFunctionStatements(tokens)
    } else {
        val elseSource = tokens.location()
        BlockNode(statements = listOf(), source = elseSource)
    }

    val node = IfNode(
        conditionalBranches = conditionalBranches,
        elseBranch = elseBranch,
        source = source
    )
    verifyConsistentBranchTermination(node)
    return node
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
        parseElement = { parseWhenBranch(tokens) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) || tokens.isNext(TokenType.KEYWORD_ELSE) },
        allowZero = true
    )

    val elseSource = tokens.location()

    val elseBranch = if (tokens.trySkip(TokenType.KEYWORD_ELSE)) {
        parseFunctionStatements(tokens)
    } else {
        null
    }

    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

    val node = WhenNode(
        expression = expression,
        conditionalBranches = branches,
        elseBranch = elseBranch,
        source = source,
        elseSource = elseSource,
    )

    verifyConsistentBranchTermination(node)

    return node
}

private fun parseWhenBranch(tokens: TokenIterator<TokenType>): WhenBranchNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_IS)
    val type = parseTypeLevelExpression(tokens)

    val targetSource = tokens.location()
    val targetFields = if (tokens.trySkip(TokenType.SYMBOL_OPEN_PAREN)) {
        val fields = parseTargetFields(tokens)
        tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
        fields
    } else {
        listOf()
    }

    val body = parseFunctionStatements(tokens)
    return WhenBranchNode(
        type = type,
        target = TargetNode.Fields(
            fields = targetFields,
            source = targetSource
        ),
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

    if (tokens.trySkip(TokenType.SYMBOL_UNDERSCORE)) {
        return TargetNode.Ignore(source = source)
    } else if (tokens.trySkip(TokenType.SYMBOL_HASH)) {
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        val elements = parseMany(
            parseElement = { parseTarget(tokens) },
            parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
            isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
            allowTrailingSeparator = true,
            allowZero = false,
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

        return TargetNode.Tuple(
            elements = elements,
            source = source
        )
    } else if (tokens.trySkip(TokenType.SYMBOL_AT)) {
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        val fields = parseTargetFields(tokens)
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

private fun parseTargetFields(tokens: TokenIterator<TokenType>): List<Pair<FieldNameNode, TargetNode>> {
    val fields = parseMany(
        parseElement = {
            tokens.skip(TokenType.SYMBOL_DOT)
            val fieldName = parseFieldName(tokens)
            tokens.skip(TokenType.KEYWORD_AS)
            val target = parseTarget(tokens)
            fieldName to target
        },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        allowTrailingSeparator = true,
        allowZero = false,
    )
    return fields
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
        throw UnexpectedTokenError(
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
private val DIVIDE_PRECEDENCE = 12
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
            InfixOperationParser(BinaryOperator.DIVIDE, TokenType.SYMBOL_FORWARD_SLASH, DIVIDE_PRECEDENCE),
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
        val operatorSource = tokens.location()

        val hasEffect = tokens.trySkip(TokenType.SYMBOL_BANG)

        val typeArguments = if (tokens.trySkip(TokenType.SYMBOL_OPEN_SQUARE_BRACKET)) {
            val typeArguments = parseMany(
                parseElement = { parseTypeLevelExpression(tokens) },
                parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
                isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
                allowZero = false,
            )
            tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
            typeArguments
        } else {
            listOf()
        }

        if (!hasEffect && !tokens.isNext(TokenType.SYMBOL_OPEN_PAREN)) {
            return TypeLevelCallNode(
                receiver = left,
                arguments = typeArguments,
                source = left.source,
                operatorSource = operatorSource,
            )
        } else {
            val (positionalArguments, namedArguments) = parseCallArguments(tokens)
            return CallNode(
                receiver = left,
                typeLevelArguments = typeArguments,
                positionalArguments = positionalArguments,
                fieldArguments = namedArguments,
                hasEffect = hasEffect,
                source = left.source,
                operatorSource = operatorSource,
            )
        }
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
            typeLevelArguments = listOf(),
            positionalArguments = positionalArguments,
            fieldArguments = namedArguments,
            source = left.source,
            operatorSource = operatorSource,
        )
    }

    override val precedence: Int
        get() = CALL_PRECEDENCE
}

private fun parseCallArguments(tokens: TokenIterator<TokenType>): Pair<List<ExpressionNode>, List<FieldArgumentNode>> {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
    val arguments = parseMany(
        parseElement = { parseArgument(tokens) },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        allowZero = true,
        allowTrailingSeparator = true
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    val positionalArguments = mutableListOf<ExpressionNode>()
    val namedArguments = mutableListOf<FieldArgumentNode>()

    for (argument in arguments) {
        when (argument) {
            is ParsedArgument.Positional ->
                if (namedArguments.isEmpty()) {
                    positionalArguments.add(argument.expression)
                } else {
                    throw PositionalArgumentAfterNamedArgumentError(source = argument.expression.source)
                }

            is ParsedArgument.Named ->
                namedArguments.add(argument.node)
        }
    }

    return Pair(positionalArguments, namedArguments)
}

private sealed class ParsedArgument {
    class Positional(val expression: ExpressionNode): ParsedArgument()
    class Named(val node: FieldArgumentNode): ParsedArgument()
}

private fun parseArgument(tokens: TokenIterator<TokenType>): ParsedArgument {
    val source = tokens.location()

    if (tokens.trySkip(TokenType.SYMBOL_ELLIPSIS)) {
        val expression = parseExpression(tokens)
        return ParsedArgument.Named(
            FieldArgumentNode.Splat(
                expression = expression,
                source = source,
            )
        )
    } else if (tokens.trySkip(TokenType.SYMBOL_DOT)) {
        val name = parseIdentifier(tokens)
        tokens.skip(TokenType.SYMBOL_EQUALS)
        val expression = parseExpression(tokens)
        return ParsedArgument.Named(
            FieldArgumentNode.Named(
                name = name,
                expression = expression,
                source = source
            )
        )
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
            fieldArguments = listOf(),
            typeLevelArguments = listOf(),
            hasEffect = false,
            source = left.source,
            operatorSource = operatorSource,
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
        val type = parseTypeLevelExpression(tokens)
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
                parseElement = { parseExpression(tokens) },
                parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
                isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
                allowZero = true,
                allowTrailingSeparator = true,
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
            val signature = parseFunctionDefinitionSignature(tokens)

            val body = if (tokens.trySkip(TokenType.SYMBOL_FAT_ARROW)) {
                val expression = parseExpression(tokens)
                val statement = ExpressionStatementNode(
                    expression = expression,
                    type = ExpressionStatementNode.Type.VALUE,
                    source = expression.source
                )
                val body = BlockNode(
                    statements = listOf(statement),
                    source = expression.source
                )
                body
            } else {
                parseFunctionStatements(tokens)
            }

            return FunctionExpressionNode(
                typeLevelParameters = signature.typeLevelParameters,
                parameters = signature.parameters,
                namedParameters = signature.namedParameters,
                returnType = signature.returnType,
                effect = signature.effect,
                body = body,
                inferReturnType = true,
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

    val effect = parseTypeLevelExpression(tokens)

    val initialState = if (tokens.trySkip(TokenType.KEYWORD_WITH_STATE)) {
        tokens.skip(TokenType.SYMBOL_OPEN_PAREN)
        val expression = parseExpression(tokens)
        tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)
        expression
    } else {
        null
    }

    val body = parseFunctionStatements(tokens)

    tokens.skip(TokenType.KEYWORD_ON)
    tokens.skip(TokenType.SYMBOL_OPEN_BRACE)
    val handlers = parseMany(
        parseElement = {
            val handlerSource = tokens.location()
            tokens.skip(TokenType.SYMBOL_DOT)
            val operationName = parseIdentifier(tokens)
            tokens.skip(TokenType.SYMBOL_EQUALS)

            val handlerFunctionSource = tokens.location()
            val handlerParameters = parseParameters(tokens)
            val handlerBody = parseFunctionStatements(tokens)
            HandlerNode(
                operationName = operationName,
                function = FunctionExpressionNode(
                    typeLevelParameters = listOf(),
                    parameters = handlerParameters.positional,
                    namedParameters = handlerParameters.named,
                    effect = null,
                    returnType = null,
                    inferReturnType = true,
                    body = handlerBody,
                    source = handlerFunctionSource
                ),
                source = handlerSource
            )
        },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_BRACE) },
        allowTrailingSeparator = true,
        allowZero = false,
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_BRACE)

    return HandleNode(
        effect = effect,
        body = body,
        initialState = initialState,
        handlers = handlers.sortedBy { handler -> handler.operationName },
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

internal fun parseTypeLevelExpression(tokens: TokenIterator<TokenType>) : TypeLevelExpressionNode {
    return parseTypeLevelExpression(tokens = tokens, precedence = Int.MIN_VALUE)
}

private fun parseTypeLevelExpression(
    tokens: TokenIterator<TokenType>,
    precedence: Int
) : TypeLevelExpressionNode {
    var left: TypeLevelExpressionNode  = parsePrimaryTypeLevelExpression(tokens)
    while (true) {
        val next = tokens.peek()
        val operationParser = TypeLevelOperationParser.lookup(next.tokenType)
        if (operationParser == null || operationParser.precedence < precedence) {
            return left
        } else {
            left = operationParser.parse(left, tokens)
        }
    }
}

private fun parsePrimaryTypeLevelExpression(
    tokens: TokenIterator<TokenType>
): TypeLevelExpressionNode {
    if (tokens.isNext(TokenType.KEYWORD_FUN_CAPITAL)) {
        return parseFunctionType(tokens)
    } else if (tokens.isNext(TokenType.SYMBOL_HASH)) {
        return parseTupleType(tokens)
    } else {
        return parseVariableReference(tokens)
    }
}

private fun parseFunctionType(tokens: TokenIterator<TokenType>): TypeLevelExpressionNode {
    val source = tokens.location()

    tokens.skip(TokenType.KEYWORD_FUN_CAPITAL)
    val typeLevelParameters = parseTypeLevelParameters(allowVariance = true, tokens = tokens)
    val parameters = parseFunctionTypeParameters(tokens)

    val effect = parseFunctionTypeEffect(tokens)
    tokens.skip(TokenType.SYMBOL_ARROW)
    val returnType = parseTypeLevelExpression(tokens)
    return FunctionTypeNode(
        typeLevelParameters = typeLevelParameters,
        positionalParameters = parameters.positional,
        namedParameters = parameters.named,
        returnType = returnType,
        effect = effect,
        source = source
    )
}

private fun parseTupleType(tokens: TokenIterator<TokenType>): TypeLevelExpressionNode {
    val source = tokens.location()

    tokens.skip(TokenType.SYMBOL_HASH)
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)

    val elementTypes = parseMany(
        parseElement = { parseTypeLevelExpression(tokens) },
        parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA) },
        isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_PAREN) },
        allowTrailingSeparator = true,
        allowZero = true,
    )
    tokens.skip(TokenType.SYMBOL_CLOSE_PAREN)

    return TupleTypeNode(
        elementTypes = elementTypes,
        source = source
    )
}

private class FunctionTypeParameters(
    val positional: List<TypeLevelExpressionNode>,
    val named: List<FunctionTypeNamedParameterNode>
)

private fun parseFunctionTypeParameters(tokens: TokenIterator<TokenType>): FunctionTypeParameters {
    tokens.skip(TokenType.SYMBOL_OPEN_PAREN)

    val positionalParameters = mutableListOf<TypeLevelExpressionNode>()
    val namedParameters = mutableListOf<FunctionTypeNamedParameterNode>()

    while (true) {
        if (tokens.trySkip(TokenType.SYMBOL_CLOSE_PAREN)) {
            return FunctionTypeParameters(
                positional = positionalParameters,
                named = namedParameters
            )
        }

        if (tokens.isNext(TokenType.SYMBOL_DOT)) {
            val parameter = parseFunctionTypeNamedParameter(tokens)
            namedParameters.add(parameter)
        } else {
            val parameter = parseTypeLevelExpression(tokens)
            if (namedParameters.isEmpty()) {
                positionalParameters.add(parameter)
            } else {
                throw PositionalParameterAfterNamedParameterError(source = parameter.source)
            }
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

private fun parseFunctionTypeNamedParameter(tokens: TokenIterator<TokenType>): FunctionTypeNamedParameterNode {
    val source = tokens.location()

    tokens.skip(TokenType.SYMBOL_DOT)
    val name = parseIdentifier(tokens)
    val type = parseTypeSpec(tokens)
    return FunctionTypeNamedParameterNode(name, type, source)
}

private interface TypeLevelOperationParser: ExpressionParser<TypeLevelExpressionNode> {
    companion object {
        private val parsers = listOf(
            TypeLevelCallParser,
            TypeLevelFieldAccessParser,
            TypeLevelUnionParser
        ).associateBy({ parser -> parser.operatorToken })

        fun lookup(tokenType: TokenType): TypeLevelOperationParser? {
            return parsers[tokenType]
        }
    }
}

private interface ExpressionParser<T> {
    val precedence: Int
    val operatorToken: TokenType

    fun parse(left: T, tokens: TokenIterator<TokenType>): T
}

private object TypeLevelCallParser : TypeLevelOperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_OPEN_SQUARE_BRACKET

    override fun parse(left: TypeLevelExpressionNode, tokens: TokenIterator<TokenType>): TypeLevelExpressionNode {
        val operatorSource = tokens.location()
        tokens.skip()
        val arguments = parseMany(
            parseElement = { parseTypeLevelExpression(tokens) },
            parseSeparator = { tokens.skip(TokenType.SYMBOL_COMMA)},
            allowZero = false,
            allowTrailingSeparator = true,
            isEnd = { tokens.isNext(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET) },
        )
        tokens.skip(TokenType.SYMBOL_CLOSE_SQUARE_BRACKET)
        return TypeLevelApplicationNode(
            receiver = left,
            arguments = arguments,
            source = left.source,
            operatorSource = operatorSource,
        )
    }

    override val precedence: Int
        get() = 14
}

private object TypeLevelFieldAccessParser : TypeLevelOperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_DOT

    override fun parse(left: TypeLevelExpressionNode, tokens: TokenIterator<TokenType>): TypeLevelExpressionNode {
        tokens.skip()
        val fieldName = parseFieldName(tokens)
        return TypeLevelFieldAccessNode(receiver = left, fieldName = fieldName, source = left.source)
    }

    override val precedence: Int
        get() = 14
}

private object TypeLevelUnionParser : TypeLevelOperationParser {
    override val operatorToken: TokenType
        get() = TokenType.SYMBOL_BAR

    override fun parse(left: TypeLevelExpressionNode, tokens: TokenIterator<TokenType>): TypeLevelExpressionNode {
        tokens.skip()
        val right = parseTypeLevelExpression(tokens, precedence = precedence)
        return TypeLevelUnionNode(elements = listOf(left, right), source = left.source)
    }

    override val precedence: Int
        get() = 13
}

private fun parseIdentifier(tokens: TokenIterator<TokenType>) = Identifier(tokens.nextValue(
    TokenType.IDENTIFIER
))

private fun verifyConsistentBranchTermination(node: ExpressionNode) {
    isTerminatingExpression(node)
}

private fun isTerminatingExpression(node: ExpressionNode): Boolean {
    val branches = expressionBranches(node)
    if (branches == null) {
        return true
    } else {
        val isTerminateds = branches.map { body ->
            body.isTerminated
        }.toSet()
        return if (isTerminateds.size == 1) {
            isTerminateds.single()
        } else {
            throw InconsistentBranchTerminationError(source = node.source)
        }
    }
}

private fun <T> parseZeroOrMore(
    parseElement: () -> T,
    parseSeparator: () -> Unit = {},
    isEnd: () -> Boolean,
    allowTrailingSeparator: Boolean = false
) : List<T> {
    return parseMany(
        parseElement = parseElement,
        parseSeparator = parseSeparator,
        isEnd = isEnd,
        allowTrailingSeparator = allowTrailingSeparator,
        allowZero = true
    )
}

private fun <T> parseMany(
    parseElement: () -> T,
    parseSeparator: () -> Unit = {},
    isEnd: () -> Boolean,
    allowTrailingSeparator: Boolean = false,
    allowZero: Boolean
) : List<T> {
    return parseMany(
        parseElement = parseElement,
        parseSeparator = parseSeparator,
        isEnd = isEnd,
        allowTrailingSeparator = allowTrailingSeparator,
        allowZero = allowZero,
        initial = mutableListOf<T>(),
        reduce = { elements, element -> elements.add(element); elements }
    )
}

private fun <T, R> parseMany(
    parseElement: () -> T,
    parseSeparator: () -> Unit,
    isEnd: () -> Boolean,
    allowTrailingSeparator: Boolean,
    allowZero: Boolean,
    initial: R,
    reduce: (R, T) -> R
) : R {
    if (allowZero && isEnd()) {
        return initial
    }

    var result = initial

    while (true) {
        result = reduce(result, parseElement())
        if (isEnd()) {
            return result
        }
        parseSeparator()
        if (allowTrailingSeparator && isEnd()) {
            return result
        }
    }
}
