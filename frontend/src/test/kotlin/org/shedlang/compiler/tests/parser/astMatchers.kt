package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.anythingOrNull
import org.shedlang.compiler.types.Variance
import java.math.BigInteger

internal fun isImport(path: Matcher<ImportPath>) = has(ImportNode::path, path)

internal fun isIf(
    conditionalBranches: Matcher<List<ConditionalBranchNode>>,
    elseBranch: Matcher<List<StatementNode>>
): Matcher<ExpressionNode> = cast(allOf(
    has(IfNode::conditionalBranches, conditionalBranches),
    has(IfNode::elseBranch, elseBranch)
))

internal fun isConditionalBranch(
    condition: Matcher<ExpressionNode>,
    body: Matcher<List<StatementNode>>
): Matcher<ConditionalBranchNode> = cast(allOf(
    has(ConditionalBranchNode::condition, condition),
    has(ConditionalBranchNode::body, body)
))

internal fun isWhen(
    expression: Matcher<ExpressionNode>,
    branches: Matcher<List<WhenBranchNode>>
): Matcher<ExpressionNode> = cast(allOf(
    has(WhenNode::expression, expression),
    has(WhenNode::branches, branches)
))

internal fun isWhenBranch(
    type: Matcher<StaticNode>,
    body: Matcher<List<StatementNode>>
): Matcher<WhenBranchNode> = allOf(
    has(WhenBranchNode::type, type),
    has(WhenBranchNode::body, body)
)

internal fun isExpressionStatement(
    expression: Matcher<ExpressionNode> = anything,
    isReturn: Matcher<Boolean> = anything
): Matcher<StatementNode> {
    return cast(allOf(
        has(ExpressionStatementNode::expression, expression),
        has(ExpressionStatementNode::isReturn, isReturn)
    ))
}

inline internal fun <reified T: ExpressionNode> isVal(
    name: Matcher<Identifier>,
    expression: Matcher<T>
): Matcher<Node> {
    return cast(allOf(
        has(ValNode::name, name),
        has(ValNode::expression, cast(expression))
    ))
}

internal fun isValType(
    name: Matcher<Identifier>,
    type: Matcher<StaticNode>
): Matcher<Node> {
    return cast(allOf(
        has(ValTypeNode::name, name),
        has(ValTypeNode::type, type)
    ))
}

internal fun isShape(
    name: Matcher<Identifier> = anything,
    staticParameters: Matcher<List<StaticParameterNode>> = anything,
    extends: Matcher<List<StaticNode>> = anything,
    fields: Matcher<List<ShapeFieldNode>> = anything
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(ShapeNode::name, name),
        has(ShapeNode::staticParameters, staticParameters),
        has(ShapeNode::extends, extends),
        has(ShapeNode::fields, fields)
    ))
}

internal fun isShapeField(
    name: Matcher<Identifier>,
    type: Matcher<StaticNode?> = anythingOrNull,
    value: Matcher<ExpressionNode?> = anythingOrNull,
    shape: Matcher<StaticNode?> = anythingOrNull
) = allOf(
    has(ShapeFieldNode::name, name),
    has(ShapeFieldNode::type, type),
    has(ShapeFieldNode::value, value),
    has(ShapeFieldNode::shape, shape)
)

internal fun isUnion(
    name: Matcher<Identifier> = anything,
    staticParameters: Matcher<List<StaticParameterNode>> = anything,
    members: Matcher<List<StaticNode>> = anything,
    superType: Matcher<StaticReferenceNode?> = anythingOrNull
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(UnionNode::name, name),
        has(UnionNode::staticParameters, staticParameters),
        has(UnionNode::members, members),
        has(UnionNode::superType, superType)
    ))
}

internal fun isFunctionDeclaration(name: Matcher<Identifier>): Matcher<ModuleStatementNode> {
    return cast(has(FunctionDeclarationNode::name, name))
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
        has(ParameterNode::type, cast(
            has(StaticReferenceNode::name, isIdentifier(typeReference))
        ))
    )
}

internal fun isUnaryOperation(
    operator: UnaryOperator,
    expression: Matcher<ExpressionNode>
): Matcher<ExpressionNode> {
    return cast(allOf(
        has(UnaryOperationNode::operator, equalTo(operator)),
        has(UnaryOperationNode::expression, expression)
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
    type: Matcher<StaticNode>
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
    typeArguments: Matcher<List<StaticNode>> = anything
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(CallNode::receiver, receiver),
        has(CallNode::staticArguments, typeArguments),
        has(CallNode::positionalArguments, positionalArguments),
        has(CallNode::namedArguments, namedArguments)
    ))
}

internal fun isPartialCall(
    receiver: Matcher<ExpressionNode> = anything,
    positionalArguments: Matcher<List<ExpressionNode>> = anything,
    namedArguments: Matcher<List<CallNamedArgumentNode>> = anything,
    typeArguments: Matcher<List<StaticNode>> = anything
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
    fieldName: Matcher<Identifier>
): Matcher<ExpressionNode> = cast(allOf(
    has(FieldAccessNode::receiver, receiver),
    has(FieldAccessNode::fieldName, has(FieldNameNode::identifier, fieldName))
))

internal fun isVariableReference(name: String) : Matcher<ExpressionNode>
    = cast(has(VariableReferenceNode::name, isIdentifier(name)))

internal fun isStaticReference(name: String) : Matcher<StaticNode>
    = cast(has(StaticReferenceNode::name, isIdentifier(name)))

internal fun isStaticFieldAccess(
    receiver: Matcher<StaticNode>,
    fieldName: Matcher<Identifier>
): Matcher<StaticNode> = cast(allOf(
    has(StaticFieldAccessNode::receiver, receiver),
    has(StaticFieldAccessNode::fieldName, fieldName)
))

internal fun isStaticApplication(
    receiver: Matcher<StaticNode>,
    arguments: Matcher<List<StaticNode>>
): Matcher<StaticNode> = cast(allOf(
    has(StaticApplicationNode::receiver, receiver),
    has(StaticApplicationNode::arguments, arguments)
))
internal fun isFunctionType(
    staticParameters: Matcher<List<StaticParameterNode>> = anything,
    positionalParameters: Matcher<List<StaticNode>> = anything,
    namedParameters: Matcher<List<ParameterNode>> = anything,
    returnType: Matcher<StaticNode> = anything,
    effects: Matcher<List<StaticNode>> = anything
): Matcher<StaticNode> = cast(allOf(
    has(FunctionTypeNode::staticParameters, staticParameters),
    has(FunctionTypeNode::positionalParameters, positionalParameters),
    has(FunctionTypeNode::namedParameters, namedParameters),
    has(FunctionTypeNode::returnType, returnType),
    has(FunctionTypeNode::effects, effects)
))

internal fun isIntLiteral(value: Matcher<Int>): Matcher<ExpressionNode>
    = cast(has(IntegerLiteralNode::value, has(BigInteger::intValueExact, value)))

internal fun isIntLiteral(value: Int) = isIntLiteral(equalTo(value))

internal fun isSymbolName(name: String): Matcher<ExpressionNode>
    = cast(has(SymbolNode::name, equalTo(name)))
