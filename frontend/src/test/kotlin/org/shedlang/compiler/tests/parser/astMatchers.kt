package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.Variance
import java.math.BigInteger

internal fun isExportNode(name: String) = isVariableReferenceNode(name)

internal fun isImportNode(
    target: Matcher<TargetNode>,
    path: Matcher<ImportPath>
) = allOf(
    has(ImportNode::target, target),
    has(ImportNode::path, path)
)

internal fun isIfNode(
    conditionalBranches: Matcher<List<ConditionalBranchNode>>,
    elseBranch: Matcher<BlockNode>
): Matcher<ExpressionNode> = cast(allOf(
    has(IfNode::conditionalBranches, conditionalBranches),
    has(IfNode::elseBranch, elseBranch)
))

internal fun isConditionalBranchNode(
    condition: Matcher<ExpressionNode>,
    body: Matcher<BlockNode>
): Matcher<ConditionalBranchNode> = cast(allOf(
    has(ConditionalBranchNode::condition, condition),
    has(ConditionalBranchNode::body, body)
))

internal fun isWhenNode(
    expression: Matcher<ExpressionNode> = anything,
    branches: Matcher<List<WhenBranchNode>> = anything,
    elseBranch: Matcher<BlockNode?> = anything,
): Matcher<ExpressionNode> = cast(allOf(
    has(WhenNode::expression, expression),
    has(WhenNode::conditionalBranches, branches),
    has(WhenNode::elseBranch, elseBranch)
))

internal fun isWhenBranchNode(
    type: Matcher<TypeLevelExpressionNode> = anything,
    target: Matcher<TargetNode.Fields> = anything,
    body: Matcher<BlockNode> = anything,
): Matcher<WhenBranchNode> = allOf(
    has(WhenBranchNode::type, type),
    has(WhenBranchNode::target, target),
    has(WhenBranchNode::body, body)
)

internal fun isHandleNode(
    effect: Matcher<TypeLevelExpressionNode> = anything,
    initialState: Matcher<ExpressionNode?> = anything,
    body: Matcher<BlockNode> = anything,
    handlers: Matcher<List<HandlerNode>> = anything,
): Matcher<ExpressionNode> = cast(allOf(
    has(HandleNode::effect, effect),
    has(HandleNode::initialState, initialState),
    has(HandleNode::body, body),
    has(HandleNode::handlers, handlers)
))

internal fun isHandlerNode(
    operationName: Matcher<Identifier> = anything,
    function: Matcher<FunctionExpressionNode> = anything
) = allOf(
    has(HandlerNode::operationName, operationName),
    has(HandlerNode::function, function)
)

internal fun isBlockNode(vararg statements: Matcher<FunctionStatementNode>) = has(BlockNode::statements, isSequence(*statements))

internal fun isExpressionStatementNode(
    expression: Matcher<ExpressionNode> = anything,
    type: Matcher<ExpressionStatementNode.Type> = anything
): Matcher<FunctionStatementNode> {
    return cast(allOf(
        has(ExpressionStatementNode::expression, expression),
        has(ExpressionStatementNode::type, type)
    ))
}

internal fun isExitNode(expression: Matcher<ExpressionNode>): Matcher<FunctionStatementNode> {
    return isExpressionStatementNode(expression = expression, type = equalTo(ExpressionStatementNode.Type.EXIT))
}

internal fun isResumeNode(
    expression: Matcher<ExpressionNode> = anything,
    newState: Matcher<ExpressionNode?> = anything,
): Matcher<FunctionStatementNode> {
    return cast(allOf(
        has(ResumeNode::expression, expression),
        has(ResumeNode::newState, newState),
    ))
}

internal fun isValNode(
    target: Matcher<TargetNode>,
    expression: Matcher<ExpressionNode> = anything
): Matcher<Node> {
    return cast(allOf(
        has(ValNode::target, target),
        has(ValNode::expression, expression)
    ))
}

internal fun isTargetIgnoreNode() = isA<TargetNode.Ignore>()

internal fun isTargetVariableNode(name: String) = isTargetVariableNode(isIdentifier(name))

internal fun isTargetVariableNode(
    name: Matcher<Identifier>
): Matcher<TargetNode> = cast(has(TargetNode.Variable::name, name))

internal fun isTargetTupleNode(
    elements: Matcher<List<TargetNode>>
): Matcher<TargetNode> = cast(has(TargetNode.Tuple::elements, elements))

internal fun isTargetFieldsNode(
    fields: Matcher<List<Pair<FieldNameNode, TargetNode>>>
): Matcher<TargetNode> = cast(has(TargetNode.Fields::fields, fields))

internal fun isValTypeNode(
    name: Matcher<Identifier>,
    type: Matcher<TypeLevelExpressionNode>
): Matcher<Node> {
    return cast(allOf(
        has(ValTypeNode::name, name),
        has(ValTypeNode::type, type)
    ))
}

internal fun isEffectDeclarationNode(
    name: Matcher<Identifier>
): Matcher<TypesModuleStatementNode> {
    return cast(allOf(
        has(EffectDeclarationNode::name, name)
    ))
}

internal fun isTypeAliasNode(
    name: Matcher<Identifier> = anything,
    expression: Matcher<TypeLevelExpressionNode> = anything
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(TypeAliasNode::name, name),
        has(TypeAliasNode::expression, expression)
    ))
}

internal fun isShapeNode(
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

internal fun isShapeFieldNode(
    name: Matcher<Identifier>,
    type: Matcher<TypeLevelExpressionNode> = anything,
    shape: Matcher<TypeLevelExpressionNode?> = anything
) = allOf(
    has(ShapeFieldNode::name, name),
    has(ShapeFieldNode::type, type),
    has(ShapeFieldNode::shape, shape)
)

internal fun isUnionNode(
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

internal fun isUnionMemberNode(
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

internal fun isVarargsDeclarationNode(
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

internal fun isFunctionDefinitionNode(
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

internal fun isTypeParameterNode(
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

internal fun isParameterNode(name: String, typeReference: String): Matcher<ParameterNode> {
    return isParameterNode(name = name, type = present(isTypeLevelReferenceNode(typeReference)))
}

internal fun isParameterNode(name: String, type: Matcher<TypeLevelExpressionNode?>): Matcher<ParameterNode> {
    return allOf(
        has(ParameterNode::name, isIdentifier(name)),
        has(ParameterNode::type, type)
    )
}

internal fun isUnaryOperationNode(
    operator: UnaryOperator,
    operand: Matcher<ExpressionNode>
): Matcher<ExpressionNode> {
    return cast(allOf(
        has(UnaryOperationNode::operator, equalTo(operator)),
        has(UnaryOperationNode::operand, operand)
    ))
}

internal fun isBinaryOperationNode(
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

internal fun isIsOperationNode(
    expression: Matcher<ExpressionNode>,
    type: Matcher<TypeLevelExpressionNode>
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(IsNode::expression, expression),
        has(IsNode::type, type)
    ))
}

internal fun isCallNode(
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

internal fun isPartialCallNode(
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

internal fun isNamedArgumentNode(
    name: Matcher<Identifier>,
    expression: Matcher<ExpressionNode>
): Matcher<FieldArgumentNode> = cast(allOf(
    has(FieldArgumentNode.Named::name, name),
    has(FieldArgumentNode.Named::expression, expression)
))

internal fun isSplatArgumentNode(
    expression: Matcher<ExpressionNode>,
): Matcher<FieldArgumentNode> = cast(
    has(FieldArgumentNode.Splat::expression, expression)
)

internal fun isTypeLevelCallNode(
    receiver: Matcher<ExpressionNode> = anything,
    arguments: Matcher<List<TypeLevelExpressionNode>> = anything,
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(TypeLevelCallNode::receiver, receiver),
        has(TypeLevelCallNode::arguments, arguments),
    ))
}

internal fun isFieldAccessNode(
    receiver: Matcher<ExpressionNode>,
    fieldName: Matcher<Identifier>,
    source: Matcher<Source> = anything
): Matcher<ExpressionNode> = cast(allOf(
    has(FieldAccessNode::receiver, receiver),
    has(FieldAccessNode::fieldName, has(FieldNameNode::identifier, fieldName)),
    has(FieldAccessNode::source, source)
))

internal fun isFieldNameNode(name: String) = has(FieldNameNode::identifier, isIdentifier(name))

internal fun isTupleNodeNode(elements: Matcher<List<ExpressionNode>>)
    = cast(has(TupleNode::elements, elements))

internal fun isVariableReferenceNode(name: String) : Matcher<Node>
    = cast(has(ReferenceNode::name, isIdentifier(name)))

internal fun isTypeLevelReferenceNode(name: String) : Matcher<Node>
    = isVariableReferenceNode(name)

internal fun isTypeLevelFieldAccessNode(
    receiver: Matcher<TypeLevelExpressionNode>,
    fieldName: Matcher<Identifier>
): Matcher<TypeLevelExpressionNode> = cast(allOf(
    has(TypeLevelFieldAccessNode::receiver, receiver),
    has(TypeLevelFieldAccessNode::fieldName, has(FieldNameNode::identifier, fieldName))
))

internal fun isTypeLevelApplicationNode(
    receiver: Matcher<TypeLevelExpressionNode>,
    arguments: Matcher<List<TypeLevelExpressionNode>>
): Matcher<TypeLevelExpressionNode> = cast(allOf(
    has(TypeLevelApplicationNode::receiver, receiver),
    has(TypeLevelApplicationNode::arguments, arguments)
))
internal fun isFunctionTypeNode(
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

internal fun isFunctionTypeNamedParameterNode(name: String, typeReference: String): Matcher<FunctionTypeNamedParameterNode> = allOf(
    has(FunctionTypeNamedParameterNode::name, isIdentifier(name)),
    has(FunctionTypeNamedParameterNode::type, isTypeLevelReferenceNode(typeReference)),
)

internal fun isTupleTypeNode(
    elementTypes: Matcher<List<TypeLevelExpressionNode>> = anything
): Matcher<TypeLevelExpressionNode> = cast(
    has(TupleTypeNode::elementTypes, elementTypes)
)

internal fun isTypeLevelUnionNode(
    elements: Matcher<List<TypeLevelExpressionNode>> = anything
): Matcher<TypeLevelExpressionNode> = cast(
    has(TypeLevelUnionNode::elements, elements)
)

internal fun isIntLiteralNode(value: Matcher<Int>): Matcher<ExpressionNode>
    = cast(has(IntegerLiteralNode::value, has(BigInteger::intValueExact, value)))

internal fun isIntLiteralNode(value: Int) = isIntLiteralNode(equalTo(value))

internal fun isUnitLiteralNode() = isA<UnitLiteralNode>()

internal fun isStringSource(contents: String, characterIndex: Int): Matcher<Source> = cast(allOf(
    has(StringSource::contents, equalTo(contents)),
    has(StringSource::characterIndex, equalTo(characterIndex))
))
