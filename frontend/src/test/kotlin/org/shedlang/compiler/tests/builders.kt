package org.shedlang.compiler.tests

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.Type

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
    namedArguments: List<CallNamedArgumentNode> = listOf()
) = CallNode(
    receiver = receiver,
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
) = FunctionNode(
    name = name,
    typeParameters = typeParameters,
    arguments = arguments,
    returnType = returnType,
    effects = effects,
    body = body,
    source = anySource()
)

fun shape(
    name: String,
    typeParameters: List<TypeParameterNode> = listOf(),
    fields: List<ShapeFieldNode> = listOf()
) = ShapeNode(
    name = name,
    typeParameters = typeParameters,
    fields = fields,
    source = anySource()
)

fun shapeField(
    name: String,
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
    name: String
) = TypeParameterNode(
    name = name,
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


fun shapeType(name: String, fields: Map<String, Type> = mapOf()) = object: ShapeType {
    override val name = name
    override val fields = fields
}
