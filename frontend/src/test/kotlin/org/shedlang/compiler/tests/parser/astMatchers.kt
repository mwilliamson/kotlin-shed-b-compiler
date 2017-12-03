package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.anythingOrNull
import org.shedlang.compiler.types.Variance

internal fun isImport(path: Matcher<ImportPath>) = has(ImportNode::path, path)

inline internal fun <reified T: ExpressionNode> isReturn(
    expression: Matcher<T>
): Matcher<StatementNode> {
    return cast(has(ReturnNode::expression, cast(expression)))
}

inline internal fun <reified T: ExpressionNode> isExpressionStatement(
    expression: Matcher<T>
): Matcher<StatementNode> {
    return cast(has(ExpressionStatementNode::expression, cast(expression)))
}

inline internal fun <reified T: ExpressionNode> isVal(
    name: Matcher<String>,
    expression: Matcher<T>
): Matcher<Node> {
    return cast(allOf(
        has(ValNode::name, name),
        has(ValNode::expression, cast(expression))
    ))
}

internal fun isShape(
    name: Matcher<String> = anything,
    typeParameters: Matcher<List<TypeParameterNode>> = anything,
    fields: Matcher<List<ShapeFieldNode>> = anything,
    tagged: Matcher<Boolean> = anything,
    hasValueForTag: Matcher<StaticNode?> = anythingOrNull
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(ShapeNode::name, name),
        has(ShapeNode::typeParameters, typeParameters),
        has(ShapeNode::fields, fields),
        has(ShapeNode::tagged, tagged),
        has(ShapeNode::hasTagValueFor, hasValueForTag)
    ))
}

internal fun isShapeField(
    name: Matcher<String>,
    type: Matcher<StaticNode>
) = allOf(
    has(ShapeFieldNode::name, name),
    has(ShapeFieldNode::type, type)
)

internal fun isUnion(
    name: Matcher<String> = anything,
    typeParameters: Matcher<List<TypeParameterNode>> = anything,
    members: Matcher<List<StaticNode>> = anything,
    superType: Matcher<StaticReferenceNode?> = anythingOrNull
): Matcher<ModuleStatementNode> {
    return cast(allOf(
        has(UnionNode::name, name),
        has(UnionNode::typeParameters, typeParameters),
        has(UnionNode::members, members),
        has(UnionNode::superType, superType)
    ))
}

internal fun isFunctionDeclaration(name: Matcher<String>): Matcher<ModuleStatementNode> {
    return cast(has(FunctionDeclarationNode::name, name))
}

internal fun isTypeParameter(
    name: Matcher<String>,
    variance: Matcher<Variance> = anything
): Matcher<StaticParameterNode> {
    return cast(allOf(
        has(TypeParameterNode::name, name),
        has(TypeParameterNode::variance, variance)
    ))
}

internal fun isEffectParameterNode(
    name: Matcher<String>
): Matcher<StaticParameterNode> {
    return cast(has(EffectParameterNode::name, name))
}

internal fun isBinaryOperation(
    operator: Operator,
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

internal fun isCallNamedArgument(
    name: Matcher<String>,
    expression: Matcher<ExpressionNode>
) = allOf(
    has(CallNamedArgumentNode::name, name),
    has(CallNamedArgumentNode::expression, expression)
)

internal fun isFieldAccess(
    receiver: Matcher<ExpressionNode>,
    fieldName: Matcher<String>
): Matcher<ExpressionNode> = cast(allOf(
    has(FieldAccessNode::receiver, receiver),
    has(FieldAccessNode::fieldName, fieldName)
))

internal fun isVariableReference(name: String) : Matcher<ExpressionNode>
    = cast(has(VariableReferenceNode::name, equalTo(name)))

internal fun isStaticReference(name: String) : Matcher<StaticNode>
    = cast(has(StaticReferenceNode::name, equalTo(name)))

internal fun isStaticFieldAccess(
    receiver: Matcher<StaticNode>,
    fieldName: Matcher<String>
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
    arguments: Matcher<List<StaticNode>> = anything,
    returnType: Matcher<StaticNode> = anything,
    effects: Matcher<List<StaticNode>> = anything
): Matcher<StaticNode> = cast(allOf(
    has(FunctionTypeNode::staticParameters, staticParameters),
    has(FunctionTypeNode::arguments, arguments),
    has(FunctionTypeNode::returnType, returnType),
    has(FunctionTypeNode::effects, effects)
))

internal fun isIntLiteral(value: Matcher<Int>): Matcher<ExpressionNode>
    = cast(has(IntegerLiteralNode::value, value))
