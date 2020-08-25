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
    expression: Matcher<ExpressionNode>,
    branches: Matcher<List<WhenBranchNode>>,
    elseBranch: Matcher<Block?>
): Matcher<ExpressionNode> = cast(allOf(
    has(WhenNode::expression, expression),
    has(WhenNode::conditionalBranches, branches),
    has(WhenNode::elseBranch, elseBranch)
))

internal fun isWhenBranch(
    type: Matcher<StaticExpressionNode>,
    body: Matcher<Block>
): Matcher<WhenBranchNode> = allOf(
    has(WhenBranchNode::type, type),
    has(WhenBranchNode::body, body)
)

internal fun isHandle(
    effect: Matcher<StaticExpressionNode> = anything,
    body: Matcher<Block> = anything,
    handlers: Matcher<List<HandlerNode>>
): Matcher<ExpressionNode> = cast(allOf(
    has(HandleNode::effect, effect),
    has(HandleNode::body, body),
    has(HandleNode::handlers, handlers)
))

internal fun isHandler(
    operationName: Matcher<Identifier>,
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

internal fun isResume(expression: Matcher<ExpressionNode>): Matcher<FunctionStatementNode> {
    return isExpressionStatement(expression = expression, type = equalTo(ExpressionStatementNode.Type.RESUME))
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
    type: Matcher<StaticExpressionNode>
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
    expression: Matcher<StaticExpressionNode> = anything
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(TypeAliasNode::name, name),
        has(TypeAliasNode::expression, expression)
    ))
}

internal fun isShape(
    name: Matcher<Identifier> = anything,
    staticParameters: Matcher<List<StaticParameterNode>> = anything,
    extends: Matcher<List<StaticExpressionNode>> = anything,
    fields: Matcher<List<ShapeFieldNode>> = anything
): Matcher<Node> {
    return cast(allOf(
        has(ShapeNode::name, name),
        has(ShapeNode::staticParameters, staticParameters),
        has(ShapeNode::extends, extends),
        has(ShapeNode::fields, fields)
    ))
}

internal fun isShapeField(
    name: Matcher<Identifier>,
    type: Matcher<StaticExpressionNode?> = anything,
    value: Matcher<ExpressionNode?> = anything,
    shape: Matcher<StaticExpressionNode?> = anything
) = allOf(
    has(ShapeFieldNode::name, name),
    has(ShapeFieldNode::type, type),
    has(ShapeFieldNode::value, value),
    has(ShapeFieldNode::shape, shape)
)

internal fun isUnion(
    name: Matcher<Identifier> = anything,
    staticParameters: Matcher<List<StaticParameterNode>> = anything,
    members: Matcher<List<UnionMemberNode>> = anything,
    superType: Matcher<ReferenceNode?> = anything
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(UnionNode::name, name),
        has(UnionNode::staticParameters, staticParameters),
        has(UnionNode::members, members),
        has(UnionNode::superType, superType)
    ))
}

internal fun isUnionMember(
    name: Matcher<Identifier> = anything,
    staticParameters: Matcher<List<StaticParameterNode>> = anything,
    extends: Matcher<List<StaticExpressionNode>> = anything,
    fields: Matcher<List<ShapeFieldNode>> = anything
): Matcher<UnionMemberNode> {
    return cast(allOf(
        has(UnionMemberNode::name, name),
        has(UnionMemberNode::staticParameters, staticParameters),
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

internal fun isFunctionDefinition(name: Matcher<Identifier>): Matcher<ModuleStatementNode> {
    return cast(has(FunctionDefinitionNode::name, name))
}

internal fun isTypeParameter(
    name: Matcher<Identifier>,
    variance: Matcher<Variance> = anything
): Matcher<StaticParameterNode> {
    return cast(allOf(
        has(TypeParameterNode::name, name),
        has(TypeParameterNode::variance, variance)
    ))
}

internal fun isEffectParameterNode(
    name: Matcher<Identifier>
): Matcher<StaticParameterNode> {
    return cast(has(EffectParameterNode::name, name))
}

internal fun isParameter(name: String, typeReference: String): Matcher<ParameterNode> {
    return allOf(
        has(ParameterNode::name, isIdentifier(name)),
        has(ParameterNode::type, isStaticReference(typeReference))
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
    type: Matcher<StaticExpressionNode>
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(IsNode::expression, expression),
        has(IsNode::type, type)
    ))
}

internal fun isCall(
    receiver: Matcher<ExpressionNode> = anything,
    positionalArguments: Matcher<List<ExpressionNode>> = anything,
    namedArguments: Matcher<List<CallNamedArgumentNode>> = anything,
    typeArguments: Matcher<List<StaticExpressionNode>> = anything,
    hasEffect: Matcher<Boolean> = anything
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(CallNode::receiver, receiver),
        has(CallNode::staticArguments, typeArguments),
        has(CallNode::positionalArguments, positionalArguments),
        has(CallNode::namedArguments, namedArguments),
        has(CallNode::hasEffect, hasEffect)
    ))
}

internal fun isPartialCall(
    receiver: Matcher<ExpressionNode> = anything,
    positionalArguments: Matcher<List<ExpressionNode>> = anything,
    namedArguments: Matcher<List<CallNamedArgumentNode>> = anything,
    typeArguments: Matcher<List<StaticExpressionNode>> = anything
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(PartialCallNode::receiver, receiver),
        has(PartialCallNode::staticArguments, typeArguments),
        has(PartialCallNode::positionalArguments, positionalArguments),
        has(PartialCallNode::namedArguments, namedArguments)
    ))
}

internal fun isCallNamedArgument(
    name: Matcher<Identifier>,
    expression: Matcher<ExpressionNode>
) = allOf(
    has(CallNamedArgumentNode::name, name),
    has(CallNamedArgumentNode::expression, expression)
)

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

internal fun isStaticReference(name: String) : Matcher<Node>
    = isVariableReference(name)

internal fun isStaticFieldAccess(
    receiver: Matcher<StaticExpressionNode>,
    fieldName: Matcher<Identifier>
): Matcher<StaticExpressionNode> = cast(allOf(
    has(StaticFieldAccessNode::receiver, receiver),
    has(StaticFieldAccessNode::fieldName, has(FieldNameNode::identifier, fieldName))
))

internal fun isStaticApplication(
    receiver: Matcher<StaticExpressionNode>,
    arguments: Matcher<List<StaticExpressionNode>>
): Matcher<StaticExpressionNode> = cast(allOf(
    has(StaticApplicationNode::receiver, receiver),
    has(StaticApplicationNode::arguments, arguments)
))
internal fun isFunctionType(
    staticParameters: Matcher<List<StaticParameterNode>> = anything,
    positionalParameters: Matcher<List<StaticExpressionNode>> = anything,
    namedParameters: Matcher<List<ParameterNode>> = anything,
    returnType: Matcher<StaticExpressionNode> = anything,
    effect: Matcher<StaticExpressionNode?> = anything
): Matcher<StaticExpressionNode> = cast(allOf(
    has(FunctionTypeNode::staticParameters, staticParameters),
    has(FunctionTypeNode::positionalParameters, positionalParameters),
    has(FunctionTypeNode::namedParameters, namedParameters),
    has(FunctionTypeNode::returnType, returnType),
    has(FunctionTypeNode::effect, effect)
))

internal fun isTupleTypeNode(
    elementTypes: Matcher<List<StaticExpressionNode>> = anything
): Matcher<StaticExpressionNode> = cast(
    has(TupleTypeNode::elementTypes, elementTypes)
)

internal fun isStaticUnion(
    elements: Matcher<List<StaticExpressionNode>> = anything
): Matcher<StaticExpressionNode> = cast(
    has(StaticUnionNode::elements, elements)
)

internal fun isIntLiteral(value: Matcher<Int>): Matcher<ExpressionNode>
    = cast(has(IntegerLiteralNode::value, has(BigInteger::intValueExact, value)))

internal fun isIntLiteral(value: Int) = isIntLiteral(equalTo(value))

internal fun isUnitLiteral() = isA<UnitLiteralNode>()

internal fun isStringSource(contents: String, characterIndex: Int): Matcher<Source> = cast(allOf(
    has(StringSource::contents, equalTo(contents)),
    has(StringSource::characterIndex, equalTo(characterIndex))
))
