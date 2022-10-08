package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.Variance
import java.math.BigInteger

internal fun isExport(name: String) = isVariableReference(name)

internal fun isImport(
    target: Matcher<TargetNode>,
    path: Matcher<ImportPath>
) = allOf(
    has(ImportNode::target, target),
    has(ImportNode::path, path)
)

internal fun isIf(
    conditionalBranches: Matcher<List<ConditionalBranchNode>>,
    elseBranch: Matcher<Block>
): Matcher<ExpressionNode> = cast(allOf(
    has(IfNode::conditionalBranches, conditionalBranches),
    has(IfNode::elseBranch, elseBranch)
))

internal fun isConditionalBranch(
    condition: Matcher<ExpressionNode>,
    body: Matcher<Block>
): Matcher<ConditionalBranchNode> = cast(allOf(
    has(ConditionalBranchNode::condition, condition),
    has(ConditionalBranchNode::body, body)
))

internal fun isWhen(
    expression: Matcher<ExpressionNode> = anything,
    branches: Matcher<List<WhenBranchNode>> = anything,
    elseBranch: Matcher<Block?> = anything,
): Matcher<ExpressionNode> = cast(allOf(
    has(WhenNode::expression, expression),
    has(WhenNode::conditionalBranches, branches),
    has(WhenNode::elseBranch, elseBranch)
))

internal fun isWhenBranch(
    type: Matcher<TypeLevelExpressionNode> = anything,
    target: Matcher<TargetNode.Fields> = anything,
    body: Matcher<Block> = anything,
): Matcher<WhenBranchNode> = allOf(
    has(WhenBranchNode::type, type),
    has(WhenBranchNode::target, target),
    has(WhenBranchNode::body, body)
)

internal fun isHandle(
    effect: Matcher<TypeLevelExpressionNode> = anything,
    initialState: Matcher<ExpressionNode?> = anything,
    body: Matcher<Block> = anything,
    handlers: Matcher<List<HandlerNode>> = anything,
): Matcher<ExpressionNode> = cast(allOf(
    has(HandleNode::effect, effect),
    has(HandleNode::initialState, initialState),
    has(HandleNode::body, body),
    has(HandleNode::handlers, handlers)
))

internal fun isHandler(
    operationName: Matcher<Identifier> = anything,
    function: Matcher<FunctionExpressionNode> = anything
) = allOf(
    has(HandlerNode::operationName, operationName),
    has(HandlerNode::function, function)
)

internal fun isBlock(vararg statements: Matcher<FunctionStatementNode>) = has(Block::statements, isSequence(*statements))

internal fun isExpressionStatement(
    expression: Matcher<ExpressionNode> = anything,
    type: Matcher<ExpressionStatementNode.Type> = anything
): Matcher<FunctionStatementNode> {
    return cast(allOf(
        has(ExpressionStatementNode::expression, expression),
        has(ExpressionStatementNode::type, type)
    ))
}

internal fun isExit(expression: Matcher<ExpressionNode>): Matcher<FunctionStatementNode> {
    return isExpressionStatement(expression = expression, type = equalTo(ExpressionStatementNode.Type.EXIT))
}

internal fun isResume(
    expression: Matcher<ExpressionNode> = anything,
    newState: Matcher<ExpressionNode?> = anything,
): Matcher<FunctionStatementNode> {
    return cast(allOf(
        has(ResumeNode::expression, expression),
        has(ResumeNode::newState, newState),
    ))
}

inline internal fun isVal(
    target: Matcher<TargetNode>,
    expression: Matcher<ExpressionNode> = anything
): Matcher<Node> {
    return cast(allOf(
        has(ValNode::target, target),
        has(ValNode::expression, expression)
    ))
}

internal fun isTargetIgnore() = isA<TargetNode.Ignore>()

internal fun isTargetVariable(name: String) = isTargetVariable(isIdentifier(name))

internal fun isTargetVariable(
    name: Matcher<Identifier>
): Matcher<TargetNode> = cast(has(TargetNode.Variable::name, name))

internal fun isTargetTuple(
    elements: Matcher<List<TargetNode>>
): Matcher<TargetNode> = cast(has(TargetNode.Tuple::elements, elements))

internal fun isTargetFields(
    fields: Matcher<List<Pair<FieldNameNode, TargetNode>>>
): Matcher<TargetNode> = cast(has(TargetNode.Fields::fields, fields))

internal fun isValType(
    name: Matcher<Identifier>,
    type: Matcher<TypeLevelExpressionNode>
): Matcher<Node> {
    return cast(allOf(
        has(ValTypeNode::name, name),
        has(ValTypeNode::type, type)
    ))
}

internal fun isEffectDeclaration(
    name: Matcher<Identifier>
): Matcher<TypesModuleStatementNode> {
    return cast(allOf(
        has(EffectDeclarationNode::name, name)
    ))
}

internal fun isTypeAlias(
    name: Matcher<Identifier> = anything,
    expression: Matcher<TypeLevelExpressionNode> = anything
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(TypeAliasNode::name, name),
        has(TypeAliasNode::expression, expression)
    ))
}

internal fun isShape(
    name: Matcher<Identifier> = anything,
    typeLevelParameters: Matcher<List<TypeLevelParameterNode>> = anything,
    extends: Matcher<List<TypeLevelExpressionNode>> = anything,
    fields: Matcher<List<ShapeFieldNode>> = anything
): Matcher<Node> {
    return cast(allOf(
        has(ShapeNode::name, name),
        has(ShapeNode::typeLevelParameters, typeLevelParameters),
        has(ShapeNode::extends, extends),
        has(ShapeNode::fields, fields)
    ))
}

internal fun isShapeField(
    name: Matcher<Identifier>,
    type: Matcher<TypeLevelExpressionNode> = anything,
    shape: Matcher<TypeLevelExpressionNode?> = anything
) = allOf(
    has(ShapeFieldNode::name, name),
    has(ShapeFieldNode::type, type),
    has(ShapeFieldNode::shape, shape)
)

internal fun isUnion(
    name: Matcher<Identifier> = anything,
    typeLevelParameters: Matcher<List<TypeLevelParameterNode>> = anything,
    members: Matcher<List<UnionMemberNode>> = anything,
    superType: Matcher<ReferenceNode?> = anything
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(UnionNode::name, name),
        has(UnionNode::typeLevelParameters, typeLevelParameters),
        has(UnionNode::members, members),
        has(UnionNode::superType, superType)
    ))
}

internal fun isUnionMember(
    name: Matcher<Identifier> = anything,
    typeLevelParameters: Matcher<List<TypeLevelParameterNode>> = anything,
    extends: Matcher<List<TypeLevelExpressionNode>> = anything,
    fields: Matcher<List<ShapeFieldNode>> = anything
): Matcher<UnionMemberNode> {
    return cast(allOf(
        has(UnionMemberNode::name, name),
        has(UnionMemberNode::typeLevelParameters, typeLevelParameters),
        has(UnionMemberNode::extends, extends),
        has(UnionMemberNode::fields, fields)
    ))
}

internal fun isVarargsDeclaration(
    name: Matcher<Identifier>,
    cons: Matcher<ExpressionNode>,
    nil: Matcher<ExpressionNode>
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(VarargsDeclarationNode::name, name),
        has(VarargsDeclarationNode::cons, cons),
        has(VarargsDeclarationNode::nil, nil)
    ))
}

internal fun isFunctionDefinition(
    name: Matcher<Identifier> = anything,
    positionalParameters: Matcher<List<ParameterNode>> = anything,
    namedParameters: Matcher<List<ParameterNode>> = anything
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(FunctionDefinitionNode::name, name),
        has(FunctionDefinitionNode::parameters, positionalParameters),
        has(FunctionDefinitionNode::namedParameters, namedParameters),
    ))
}

internal fun isTypeParameter(
    name: Matcher<Identifier>,
    variance: Matcher<Variance> = anything
): Matcher<TypeLevelParameterNode> {
    return cast(allOf(
        has(TypeParameterNode::name, name),
        has(TypeParameterNode::variance, variance)
    ))
}

internal fun isEffectParameterNode(
    name: Matcher<Identifier>
): Matcher<TypeLevelParameterNode> {
    return cast(has(EffectParameterNode::name, name))
}

internal fun isParameter(name: String, typeReference: String): Matcher<ParameterNode> {
    return isParameter(name = name, type = present(isTypeLevelReference(typeReference)))
}

internal fun isParameter(name: String, type: Matcher<TypeLevelExpressionNode?>): Matcher<ParameterNode> {
    return allOf(
        has(ParameterNode::name, isIdentifier(name)),
        has(ParameterNode::type, type)
    )
}

internal fun isUnaryOperation(
    operator: UnaryOperator,
    operand: Matcher<ExpressionNode>
): Matcher<ExpressionNode> {
    return cast(allOf(
        has(UnaryOperationNode::operator, equalTo(operator)),
        has(UnaryOperationNode::operand, operand)
    ))
}

internal fun isBinaryOperation(
    operator: BinaryOperator,
    left: Matcher<ExpressionNode>,
    right: Matcher<ExpressionNode>
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(BinaryOperationNode::operator, equalTo(operator)),
        has(BinaryOperationNode::left, left),
        has(BinaryOperationNode::right, right)
    ))
}

internal fun isIsOperation(
    expression: Matcher<ExpressionNode>,
    type: Matcher<TypeLevelExpressionNode>
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(IsNode::expression, expression),
        has(IsNode::type, type)
    ))
}

internal fun isCall(
    receiver: Matcher<ExpressionNode> = anything,
    positionalArguments: Matcher<List<ExpressionNode>> = anything,
    fieldArguments: Matcher<List<FieldArgumentNode>> = anything,
    typeArguments: Matcher<List<TypeLevelExpressionNode>> = anything,
    hasEffect: Matcher<Boolean> = anything
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(CallNode::receiver, receiver),
        has(CallNode::typeLevelArguments, typeArguments),
        has(CallNode::positionalArguments, positionalArguments),
        has(CallNode::fieldArguments, fieldArguments),
        has(CallNode::hasEffect, hasEffect)
    ))
}

internal fun isPartialCall(
    receiver: Matcher<ExpressionNode> = anything,
    positionalArguments: Matcher<List<ExpressionNode>> = anything,
    fieldArguments: Matcher<List<FieldArgumentNode>> = anything,
    typeArguments: Matcher<List<TypeLevelExpressionNode>> = anything
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(PartialCallNode::receiver, receiver),
        has(PartialCallNode::typeLevelArguments, typeArguments),
        has(PartialCallNode::positionalArguments, positionalArguments),
        has(PartialCallNode::fieldArguments, fieldArguments)
    ))
}

internal fun isNamedArgument(
    name: Matcher<Identifier>,
    expression: Matcher<ExpressionNode>
): Matcher<FieldArgumentNode> = cast(allOf(
    has(FieldArgumentNode.Named::name, name),
    has(FieldArgumentNode.Named::expression, expression)
))

internal fun isSplatArgument(
    expression: Matcher<ExpressionNode>,
): Matcher<FieldArgumentNode> = cast(
    has(FieldArgumentNode.Splat::expression, expression)
)

internal fun isTypeLevelCall(
    receiver: Matcher<ExpressionNode> = anything,
    arguments: Matcher<List<TypeLevelExpressionNode>> = anything,
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(TypeLevelCallNode::receiver, receiver),
        has(TypeLevelCallNode::arguments, arguments),
    ))
}

internal fun isFieldAccess(
    receiver: Matcher<ExpressionNode>,
    fieldName: Matcher<Identifier>,
    source: Matcher<Source> = anything
): Matcher<ExpressionNode> = cast(allOf(
    has(FieldAccessNode::receiver, receiver),
    has(FieldAccessNode::fieldName, has(FieldNameNode::identifier, fieldName)),
    has(FieldAccessNode::source, source)
))

internal fun isFieldName(name: String) = has(FieldNameNode::identifier, isIdentifier(name))

internal fun isTupleNode(elements: Matcher<List<ExpressionNode>>)
    = cast(has(TupleNode::elements, elements))

internal fun isVariableReference(name: String) : Matcher<Node>
    = cast(has(ReferenceNode::name, isIdentifier(name)))

internal fun isTypeLevelReference(name: String) : Matcher<Node>
    = isVariableReference(name)

internal fun isTypeLevelFieldAccess(
    receiver: Matcher<TypeLevelExpressionNode>,
    fieldName: Matcher<Identifier>
): Matcher<TypeLevelExpressionNode> = cast(allOf(
    has(TypeLevelFieldAccessNode::receiver, receiver),
    has(TypeLevelFieldAccessNode::fieldName, has(FieldNameNode::identifier, fieldName))
))

internal fun isTypeLevelApplication(
    receiver: Matcher<TypeLevelExpressionNode>,
    arguments: Matcher<List<TypeLevelExpressionNode>>
): Matcher<TypeLevelExpressionNode> = cast(allOf(
    has(TypeLevelApplicationNode::receiver, receiver),
    has(TypeLevelApplicationNode::arguments, arguments)
))
internal fun isFunctionType(
    typeLevelParameters: Matcher<List<TypeLevelParameterNode>> = anything,
    positionalParameters: Matcher<List<TypeLevelExpressionNode>> = anything,
    namedParameters: Matcher<List<FunctionTypeNamedParameterNode>> = anything,
    returnType: Matcher<TypeLevelExpressionNode> = anything,
    effect: Matcher<TypeLevelExpressionNode?> = anything
): Matcher<TypeLevelExpressionNode> = cast(allOf(
    has(FunctionTypeNode::typeLevelParameters, typeLevelParameters),
    has(FunctionTypeNode::positionalParameters, positionalParameters),
    has(FunctionTypeNode::namedParameters, namedParameters),
    has(FunctionTypeNode::returnType, returnType),
    has(FunctionTypeNode::effect, effect)
))

internal fun isFunctionTypeNamedParameter(name: String, typeReference: String): Matcher<FunctionTypeNamedParameterNode> = allOf(
    has(FunctionTypeNamedParameterNode::name, isIdentifier(name)),
    has(FunctionTypeNamedParameterNode::type, isTypeLevelReference(typeReference)),
)

internal fun isTupleTypeNode(
    elementTypes: Matcher<List<TypeLevelExpressionNode>> = anything
): Matcher<TypeLevelExpressionNode> = cast(
    has(TupleTypeNode::elementTypes, elementTypes)
)

internal fun isTypeLevelUnion(
    elements: Matcher<List<TypeLevelExpressionNode>> = anything
): Matcher<TypeLevelExpressionNode> = cast(
    has(TypeLevelUnionNode::elements, elements)
)

internal fun isIntLiteral(value: Matcher<Int>): Matcher<ExpressionNode>
    = cast(has(IntegerLiteralNode::value, has(BigInteger::intValueExact, value)))

internal fun isIntLiteral(value: Int) = isIntLiteral(equalTo(value))

internal fun isUnitLiteral() = isA<UnitLiteralNode>()

internal fun isStringSource(contents: String, characterIndex: Int): Matcher<Source> = cast(allOf(
    has(StringSource::contents, equalTo(contents)),
    has(StringSource::characterIndex, equalTo(characterIndex))
))
