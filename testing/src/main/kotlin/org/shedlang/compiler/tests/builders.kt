package org.shedlang.compiler.tests

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

fun anySource(): Source {
    return StringSource(filename = "<string>", contents = "", characterIndex = 0)
}

fun ifStatement(
    condition: ExpressionNode = literalBool(true),
    trueBranch: List<StatementNode> = listOf(),
    elseBranch: List<StatementNode> = listOf()
): StatementNode {
    return ExpressionStatementNode(
        expression = ifExpression(
            condition = condition,
            trueBranch = trueBranch,
            elseBranch = elseBranch
        ),
        isReturn = false,
        source = anySource()
    )
}

fun ifExpression(
    condition: ExpressionNode = literalBool(true),
    trueBranch: List<StatementNode> = listOf(),
    elseBranch: List<StatementNode> = listOf()
): IfNode {
    return ifExpression(
        conditionalBranches = listOf(
            conditionalBranch(
                condition = condition,
                body = trueBranch
            )
        ),
        elseBranch = elseBranch
    )
}

fun ifExpression(
    conditionalBranches: List<ConditionalBranchNode>,
    elseBranch: List<StatementNode> = listOf()
): IfNode {
    return IfNode(
        conditionalBranches = conditionalBranches,
        elseBranch = elseBranch,
        source = anySource()
    )
}

fun conditionalBranch(
    condition: ExpressionNode,
    body: List<StatementNode>
) = ConditionalBranchNode(
    condition = condition,
    body = body,
    source = anySource()
)

fun whenExpression(
    expression: ExpressionNode,
    branches: List<WhenBranchNode> = listOf()
) = WhenNode(expression = expression, branches = branches, source = anySource())

fun whenBranch(
    type: StaticNode,
    body: List<StatementNode> = listOf()
) = WhenBranchNode(
    type = type,
    body = body,
    source = anySource()
)

fun expressionStatement(
    expression: ExpressionNode = expression(),
    isReturn: Boolean = false,
    source: Source = anySource()
) = ExpressionStatementNode(expression, isReturn = isReturn, source = source)

fun valStatement(
    name: String = "<val name>",
    expression: ExpressionNode = expression()
) = ValNode(
    name = Identifier(name),
    expression = expression,
    source = anySource()
)

fun variableBinder(
    name: String
) = valStatement(name = name)

fun valType(
    name: String = "<val name>",
    type: StaticNode
) = ValTypeNode(
    name = Identifier(name),
    type = type,
    source = anySource()
)

fun expression() = literalString("<expression>")
fun literalUnit() = UnitLiteralNode(anySource())
fun literalBool(
    value: Boolean = false,
    source: Source = anySource()
) = BooleanLiteralNode(value, source = source)
fun literalInt(value: Int = 0) = IntegerLiteralNode(value.toBigInteger(), anySource())
fun literalString(value: String = "") = StringLiteralNode(value, anySource())
fun literalChar(value: Char = '!') = CharacterLiteralNode(value.toInt(), anySource())
fun symbolName(name: String) = SymbolNode(name, anySource())
fun variableReference(name: String) = VariableReferenceNode(Identifier(name), anySource())

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

fun partialCall(
    receiver: ExpressionNode,
    positionalArguments: List<ExpressionNode> = listOf(),
    namedArguments: List<CallNamedArgumentNode> = listOf(),
    staticArguments: List<StaticNode> = listOf()
) = PartialCallNode(
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
    name = Identifier(name),
    expression = expression,
    source = anySource()
)

fun fieldAccess(
    receiver: ExpressionNode,
    fieldName: String
) = FieldAccessNode(
    receiver = receiver,
    fieldName = FieldNameNode(Identifier(fieldName), source = anySource()),
    source = anySource()
)

fun function(
    name: String = "f",
    staticParameters: List<StaticParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effects: List<StaticNode> = listOf(),
    returnType: StaticNode = staticReference("Unit"),
    body: List<StatementNode> = listOf()
) = FunctionDeclarationNode(
    name = Identifier(name),
    staticParameters = staticParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Statements(body),
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effects: List<StaticNode> = listOf(),
    returnType: StaticNode? = null,
    body: List<StatementNode> = listOf()
) = FunctionExpressionNode(
    staticParameters = typeParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Statements(body),
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effects: List<StaticNode> = listOf(),
    returnType: StaticNode? = staticReference("Unit"),
    body: ExpressionNode
) = FunctionExpressionNode(
    staticParameters = typeParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effects = effects,
    body = FunctionBody.Expression(body),
    source = anySource()
)

fun shape(
    name: String = "Shape",
    staticParameters: List<StaticParameterNode> = listOf(),
    extends: List<StaticNode> = listOf(),
    fields: List<ShapeFieldNode> = listOf()
) = ShapeNode(
    name = Identifier(name),
    staticParameters = staticParameters,
    extends = extends,
    fields = fields,
    source = anySource()
)

fun shapeField(
    name: String = "field",
    type: StaticNode? = null,
    value: ExpressionNode? = null,
    shape: StaticNode? = null
) = ShapeFieldNode(
    shape = shape,
    name = Identifier(name),
    type = type,
    value = value,
    source = anySource()
)

fun union(
    name: String = "Union",
    members: List<StaticNode> = listOf(),
    staticParameters: List<StaticParameterNode> = listOf(),
    superType: StaticReferenceNode? = null
) = UnionNode(
    name = Identifier(name),
    staticParameters = staticParameters,
    superType = superType,
    members = members,
    source = anySource()
)

fun typeParameter(
    name: String = "T",
    variance: Variance = Variance.INVARIANT
) = TypeParameterNode(
    name = Identifier(name),
    variance = variance,
    source = anySource()
)

fun effectParameterDeclaration(
    name: String
) = EffectParameterNode(
    name = Identifier(name),
    source = anySource()
)

fun declaration(name: String) = parameter(name)

fun parameter(
    name: String = "x",
    type: StaticNode = staticReference("Int")
) = ParameterNode(
    name = Identifier(name),
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

fun typesModule(
    imports: List<ImportNode> = listOf(),
    body: List<ValTypeNode> = listOf()
) = TypesModuleNode(
    imports = imports,
    body = body,
    source = anySource()
)

fun import(
    path: ImportPath = ImportPath.absolute(listOf("Module"))
) = ImportNode(path = path, source = anySource())

fun staticReference(name: String) = StaticReferenceNode(Identifier(name), anySource())
fun staticFieldAccess(
    receiver: StaticNode,
    fieldName: String
) = StaticFieldAccessNode(
    receiver = receiver,
    fieldName = Identifier(fieldName),
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
    positionalParameters: List<StaticNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    returnType: StaticNode,
    effects: List<StaticNode> = listOf()
) = FunctionTypeNode(
    staticParameters = staticParameters,
    positionalParameters = positionalParameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effects = effects,
    source = anySource()
)

fun parametrizedShapeType(
    name: String,
    parameters: List<TypeParameter>,
    fields: List<Field> = listOf()
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
    fields: List<Field> = listOf(),
    typeParameters: List<TypeParameter> = listOf(),
    typeArguments: List<Type> = listOf(),
    shapeId: Int = freshShapeId()
) = lazyShapeType(
    shapeId = shapeId,
    name = Identifier(name),
    getFields = lazy { fields },
    staticParameters = typeParameters,
    staticArguments = typeArguments
)

fun field(name: String, type: Type, isConstant: Boolean = false, shapeId: Int = freshShapeId()) = Field(
    shapeId,
    Identifier(name),
    type,
    isConstant = isConstant
)


fun parametrizedUnionType(
    name: String,
    parameters: List<TypeParameter> = listOf(invariantTypeParameter("T")),
    members: List<ShapeType> = listOf()
) = TypeFunction(
    type = LazyUnionType(
        name = Identifier(name),
        getMembers = lazy { members },
        staticArguments = parameters
    ),
    parameters = parameters
)

fun unionType(
    name: String = "Union",
    members: List<Type> = listOf()
) = LazyUnionType(
    name = Identifier(name),
    getMembers = lazy { members },
    staticArguments = listOf()
)

fun moduleType(fields: Map<String, Type>) = ModuleType(
    fields = fields.mapKeys { (name, type) -> Identifier(name) }
)
