package org.shedlang.compiler.tests

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

fun anySource(): Source {
    return StringSource(filename = "<string>", contents = "", characterIndex = 0)
}

fun ifStatement(
    condition: ExpressionNode = literalBool(true),
    trueBranch: List<StatementNode> = listOf(),
    falseBranch: List<StatementNode> = listOf()
): IfStatementNode {
    return IfStatementNode(condition, trueBranch, falseBranch, anySource())
}

fun expressionStatement(expression: ExpressionNode = expression())
    = ExpressionStatementNode(expression, anySource())

fun valStatement(
    name: String = "<val name>",
    expression: ExpressionNode = expression()
) = ValNode(
    name = name,
    expression = expression,
    source = anySource()
)

fun expression() = literalString("<expression>")
fun literalUnit() = UnitLiteralNode(anySource())
fun literalBool(
    value: Boolean = false,
    source: Source = anySource()
) = BooleanLiteralNode(value, source = source)
fun literalInt(value: Int = 0) = IntegerLiteralNode(value, anySource())
fun literalString(value: String = "") = StringLiteralNode(value, anySource())
fun variableReference(name: String) = VariableReferenceNode(name, anySource())
fun returns(expression: ExpressionNode = expression())
    = ReturnNode(expression, anySource())

fun binaryOperation(
    operator: Operator,
    left: ExpressionNode,
    right: ExpressionNode
) = BinaryOperationNode(
    operator = operator,
    left = left,
    right = right,
    source = anySource()
)

fun isOperation(
    expression: ExpressionNode,
    type: StaticNode
) = IsNode(
    expression = expression,
    type = type,
    source = anySource()
)

fun call(
    receiver: ExpressionNode,
    positionalArguments: List<ExpressionNode> = listOf(),
    namedArguments: List<CallNamedArgumentNode> = listOf(),
    staticArguments: List<StaticNode> = listOf()
) = CallNode(
    receiver = receiver,
    staticArguments = staticArguments,
    positionalArguments = positionalArguments,
    namedArguments = namedArguments,
    source = anySource()
)

fun callNamedArgument(
    name: String,
    expression: ExpressionNode
) = CallNamedArgumentNode(
    name = name,
    expression = expression,
    source = anySource()
)

fun fieldAccess(
    receiver: ExpressionNode,
    fieldName: String
) = FieldAccessNode(
    receiver = receiver,
    fieldName = fieldName,
    source = anySource()
)

fun function(
    name: String = "f",
    staticParameters: List<StaticParameterNode> = listOf(),
    arguments: List<ArgumentNode> = listOf(),
    effects: List<StaticNode> = listOf(),
    returnType: StaticNode = staticReference("Unit"),
    body: List<StatementNode> = listOf()
) = FunctionDeclarationNode(
    name = name,
    staticParameters = staticParameters,
    arguments = arguments,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Statements(body),
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    arguments: List<ArgumentNode> = listOf(),
    effects: List<StaticNode> = listOf(),
    returnType: StaticNode? = null,
    body: List<StatementNode> = listOf()
) = FunctionExpressionNode(
    staticParameters = typeParameters,
    arguments = arguments,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Statements(body),
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    arguments: List<ArgumentNode> = listOf(),
    effects: List<StaticNode> = listOf(),
    returnType: StaticNode? = staticReference("Unit"),
    body: ExpressionNode
) = FunctionExpressionNode(
    staticParameters = typeParameters,
    arguments = arguments,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Expression(body),
    source = anySource()
)

fun shape(
    name: String = "Shape",
    typeParameters: List<TypeParameterNode> = listOf(),
    tag: Boolean = false,
    hasTagValueFor: StaticNode? = null,
    fields: List<ShapeFieldNode> = listOf()
) = ShapeNode(
    name = name,
    typeParameters = typeParameters,
    tagged = tag,
    hasTagValueFor = hasTagValueFor,
    fields = fields,
    source = anySource()
)

fun shapeField(
    name: String = "field",
    type: StaticNode
) = ShapeFieldNode(
    name = name,
    type = type,
    source = anySource()
)

fun union(
    name: String = "Union",
    members: List<StaticNode> = listOf(),
    typeParameters: List<TypeParameterNode> = listOf(),
    tagged: Boolean = false,
    superType: StaticReferenceNode? = null
) = UnionNode(
    name = name,
    typeParameters = typeParameters,
    tagged = tagged,
    superType = superType,
    members = members,
    source = anySource()
)

fun typeParameter(
    name: String,
    variance: Variance = Variance.INVARIANT
) = TypeParameterNode(
    name = name,
    variance = variance,
    source = anySource()
)

fun effectParameterDeclaration(
    name: String
) = EffectParameterNode(
    name = name,
    source = anySource()
)

fun argument(
    name: String = "x",
    type: StaticNode = staticReference("Int")
) = ArgumentNode(
    name = name,
    type = type,
    source = anySource()
)

fun module(
    body: List<ModuleStatementNode> = listOf(),
    imports: List<ImportNode> = listOf()
) = ModuleNode(
    imports = imports,
    body = body,
    source = anySource()
)

fun import(path: ImportPath) = ImportNode(path = path, source = anySource())

fun staticReference(name: String) = StaticReferenceNode(name, anySource())
fun staticFieldAccess(
    receiver: StaticNode,
    fieldName: String
) = StaticFieldAccessNode(
    receiver = receiver,
    fieldName = fieldName,
    source = anySource()
)
fun staticApplication(
    receiver: StaticNode,
    arguments: List<StaticNode>
) = StaticApplicationNode(
    receiver = receiver,
    arguments = arguments,
    source = anySource()
)
fun functionTypeNode(
    staticParameters: List<StaticParameterNode> = listOf(),
    arguments: List<StaticNode> = listOf(),
    returnType: StaticNode,
    effects: List<StaticNode> = listOf()
) = FunctionTypeNode(
    staticParameters = staticParameters,
    arguments = arguments,
    returnType = returnType,
    effects = effects,
    source = anySource()
)

fun parametrizedShapeType(
    name: String,
    parameters: List<TypeParameter>,
    fields: Map<String, Type> = mapOf()
) = TypeFunction(
    type = shapeType(
        name = name,
        fields = fields,
        typeParameters = parameters,
        typeArguments = parameters
    ),
    parameters = parameters
)

fun shapeType(
    name: String = "Shape",
    fields: Map<String, Type> = mapOf(),
    typeParameters: List<TypeParameter> = listOf(),
    typeArguments: List<Type> = listOf(),
    tag: Tag? = null,
    tagValue: TagValue? = null
) = LazyShapeType(
    name = name,
    getFields = lazy { fields },
    shapeId = freshShapeId(),
    typeParameters = typeParameters,
    typeArguments = typeArguments,
    tag = tag,
    getTagValue = lazy { tagValue }
)


fun parametrizedUnionType(
    name: String,
    parameters: List<TypeParameter> = listOf(TypeParameter("T", variance = Variance.INVARIANT)),
    members: List<Type> = listOf(),
    tag: Tag = Tag(name)
) = TypeFunction(
    type = LazyUnionType(
        name = name,
        getMembers = lazy { members },
        tag = tag,
        typeArguments = parameters
    ),
    parameters = parameters
)

fun unionType(
    name: String = "Union",
    members: List<Type> = listOf(),
    tag: Tag = Tag(name)
) = LazyUnionType(
    name = name,
    getMembers = lazy { members },
    tag = tag,
    typeArguments = listOf()
)
