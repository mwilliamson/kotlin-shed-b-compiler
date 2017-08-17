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
fun literalBool(value: Boolean = false) = BooleanLiteralNode(value, anySource())
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
    type: TypeNode
) = IsNode(
    expression = expression,
    type = type,
    source = anySource()
)

fun call(
    receiver: ExpressionNode,
    positionalArguments: List<ExpressionNode> = listOf(),
    namedArguments: List<CallNamedArgumentNode> = listOf(),
    typeArguments: List<TypeNode> = listOf()
) = CallNode(
    receiver = receiver,
    typeArguments = typeArguments,
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
    typeParameters: List<TypeParameterNode> = listOf(),
    arguments: List<ArgumentNode> = listOf(),
    effects: List<VariableReferenceNode> = listOf(),
    returnType: TypeNode = typeReference("Unit"),
    body: List<StatementNode> = listOf()
) = FunctionDeclarationNode(
    name = name,
    typeParameters = typeParameters,
    arguments = arguments,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Statements(body),
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    arguments: List<ArgumentNode> = listOf(),
    effects: List<VariableReferenceNode> = listOf(),
    returnType: TypeNode = typeReference("Unit"),
    body: List<StatementNode> = listOf()
) = FunctionExpressionNode(
    typeParameters = typeParameters,
    arguments = arguments,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Statements(body),
    source = anySource()
)

fun shape(
    name: String = "Shape",
    typeParameters: List<TypeParameterNode> = listOf(),
    fields: List<ShapeFieldNode> = listOf()
) = ShapeNode(
    name = name,
    typeParameters = typeParameters,
    fields = fields,
    source = anySource()
)

fun shapeField(
    name: String = "field",
    type: TypeNode
) = ShapeFieldNode(
    name = name,
    type = type,
    source = anySource()
)

fun union(
    name: String = "Union",
    members: List<TypeNode>,
    typeParameters: List<TypeParameterNode> = listOf()
) = UnionNode(
    name = name,
    typeParameters = typeParameters,
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

fun argument(
    name: String = "x",
    type: TypeNode = typeReference("Int")
) = ArgumentNode(
    name = name,
    type = type,
    source = anySource()
)

fun module(
    body: List<ModuleStatementNode> = listOf(),
    imports: List<ImportNode> = listOf()
) = ModuleNode(
    path = listOf(),
    imports = imports,
    body = body,
    source = anySource()
)

fun import(path: ImportPath) = ImportNode(path = path, source = anySource())

fun typeReference(name: String) = TypeReferenceNode(name, anySource())
fun typeApplication(
    receiver: TypeNode,
    arguments: List<TypeNode>
) = TypeApplicationNode(
    receiver = receiver,
    arguments = arguments,
    source = anySource()
)
fun functionType(
    arguments: List<TypeNode> = listOf(),
    returnType: TypeNode
) = FunctionTypeNode(
    arguments = arguments,
    returnType = returnType,
    source = anySource()
)

fun parametrizedShapeType(
    name: String,
    parameters: List<TypeParameter>,
    fields: Map<String, Type> = mapOf()
) = TypeFunction(
    type = object: ShapeType {
        override val name = name
        override val fields = fields
        override val shapeId = freshShapeId()
        override val typeParameters = parameters
        override val typeArguments = parameters

        override val shortDescription = name
    },
    parameters = parameters
)

fun shapeType(name: String = "Shape", fields: Map<String, Type> = mapOf()) = object: ShapeType {
    override val name = name
    override val fields = fields
    override val shapeId = freshShapeId()
    override val typeParameters: List<TypeParameter> = listOf()
    override val typeArguments: List<Type> = listOf()

    override val shortDescription: String
        get() = name
}


fun parametrizedUnionType(
    name: String,
    parameters: List<TypeParameter>,
    members: List<Type>
) = TypeFunction(
    type = object: UnionType {
        override val name = name
        override val members = members
        override val typeArguments = parameters

        override val shortDescription = name
    },
    parameters = parameters
)

fun unionType(name: String, members: List<Type>) = SimpleUnionType(name = name, members = members)
