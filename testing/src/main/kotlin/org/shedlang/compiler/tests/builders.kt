package org.shedlang.compiler.tests

import org.shedlang.compiler.Types
import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

fun anySource(): Source {
    return StringSource(filename = "<string>", contents = "", characterIndex = 0)
}

fun ifStatement(
    condition: ExpressionNode = literalBool(true),
    trueBranch: List<FunctionStatementNode> = listOf(),
    elseBranch: List<FunctionStatementNode> = listOf()
): FunctionStatementNode {
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
    trueBranch: List<FunctionStatementNode> = listOf(),
    elseBranch: List<FunctionStatementNode> = listOf()
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
    elseBranch: List<FunctionStatementNode> = listOf()
): IfNode {
    return IfNode(
        conditionalBranches = conditionalBranches,
        elseBranch = elseBranch,
        source = anySource()
    )
}

fun conditionalBranch(
    condition: ExpressionNode,
    body: List<FunctionStatementNode>
) = ConditionalBranchNode(
    condition = condition,
    body = body,
    source = anySource()
)

fun whenExpression(
    expression: ExpressionNode,
    branches: List<WhenBranchNode> = listOf(),
    elseBranch: List<FunctionStatementNode>? = null
) = WhenNode(
    expression = expression,
    branches = branches,
    elseBranch = elseBranch,
    source = anySource()
)

fun whenBranch(
    type: StaticExpressionNode,
    body: List<FunctionStatementNode> = listOf()
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
) = valStatement(
    target = targetVariable(name = name),
    expression = expression
)

fun valStatement(
    target: TargetNode,
    expression: ExpressionNode = expression()
) = ValNode(
    target = target,
    expression = expression,
    source = anySource()
)

fun targetVariable(name: String = "<target name>") = TargetNode.Variable(
    Identifier(name),
    source = anySource()
)

fun targetTuple(elements: List<TargetNode>) = TargetNode.Tuple(
    elements,
    source = anySource()
)

fun targetFields(fields: List<Pair<FieldNameNode, TargetNode>>) = TargetNode.Fields(
    fields,
    source = anySource()
)

fun variableBinder(
    name: String
): VariableBindingNode = parameter(name = name)

fun valType(
    name: String = "<val name>",
    type: StaticExpressionNode
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
fun literalCodePoint(value: Char = '!') = CodePointLiteralNode(value.toInt(), anySource())
fun symbolName(name: String) = SymbolNode(name, anySource())
fun tupleNode(elements: List<ExpressionNode>) = TupleNode(elements, anySource())
fun variableReference(name: String) = VariableReferenceNode(Identifier(name), anySource())

fun unaryOperation(
    operator: UnaryOperator,
    operand: ExpressionNode
) = UnaryOperationNode(
    operator = operator,
    operand = operand,
    source = anySource()
)

fun binaryOperation(
    operator: BinaryOperator,
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
    type: StaticExpressionNode
) = IsNode(
    expression = expression,
    type = type,
    source = anySource()
)

fun call(
    receiver: ExpressionNode,
    positionalArguments: List<ExpressionNode> = listOf(),
    namedArguments: List<CallNamedArgumentNode> = listOf(),
    staticArguments: List<StaticExpressionNode> = listOf(),
    hasEffect: Boolean = false
) = CallNode(
    receiver = receiver,
    staticArguments = staticArguments,
    positionalArguments = positionalArguments,
    namedArguments = namedArguments,
    hasEffect = hasEffect,
    source = anySource()
)

fun partialCall(
    receiver: ExpressionNode,
    positionalArguments: List<ExpressionNode> = listOf(),
    namedArguments: List<CallNamedArgumentNode> = listOf(),
    staticArguments: List<StaticExpressionNode> = listOf()
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
    fieldName = fieldName(fieldName),
    source = anySource()
)

fun fieldName(
    name: String,
    source: Source = anySource()
) = FieldNameNode(Identifier(name), source = source)

fun function(
    name: String = "f",
    staticParameters: List<StaticParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effects: List<StaticExpressionNode> = listOf(),
    returnType: StaticExpressionNode = staticReference("Unit"),
    body: List<FunctionStatementNode> = listOf()
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
    effects: List<StaticExpressionNode> = listOf(),
    returnType: StaticExpressionNode? = null,
    body: List<FunctionStatementNode> = listOf()
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
    effects: List<StaticExpressionNode> = listOf(),
    returnType: StaticExpressionNode? = staticReference("Unit"),
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

fun typeAliasDeclaration(
    name: String,
    expression: StaticExpressionNode
) = TypeAliasNode(
    name = Identifier(name),
    expression = expression,
    source = anySource()
)

fun shape(
    name: String = "Shape",
    staticParameters: List<StaticParameterNode> = listOf(),
    extends: List<StaticExpressionNode> = listOf(),
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
    type: StaticExpressionNode? = null,
    value: ExpressionNode? = null,
    shape: StaticExpressionNode? = null
) = ShapeFieldNode(
    shape = shape,
    name = Identifier(name),
    type = type,
    value = value,
    source = anySource()
)

fun union(
    name: String = "Union",
    members: List<UnionMemberNode> = listOf(),
    staticParameters: List<StaticParameterNode> = listOf(),
    superType: StaticReferenceNode? = null
) = UnionNode(
    name = Identifier(name),
    staticParameters = staticParameters,
    superType = superType,
    members = members,
    source = anySource()
)

fun unionMember(
    name: String,
    staticParameters: List<StaticParameterNode> = listOf(),
    extends: List<StaticExpressionNode> = listOf(),
    fields: List<ShapeFieldNode> = listOf()
) = UnionMemberNode(
    name = Identifier(name),
    staticParameters = staticParameters,
    extends = extends,
    fields = fields,
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
    type: StaticExpressionNode = staticReference("Int")
) = ParameterNode(
    name = Identifier(name),
    type = type,
    source = anySource()
)

fun module(
    body: List<ModuleStatementNode> = listOf(),
    imports: List<ImportNode> = listOf(),
    exports: List<ExportNode> = listOf()
) = ModuleNode(
    exports = exports,
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

fun export(name: String) = ExportNode(
    name = Identifier(name),
    source = anySource()
)

fun import(
    name: Identifier,
    path: ImportPath = ImportPath.absolute(listOf("Module"))
) = import(
    target = TargetNode.Variable(name, source = anySource()),
    path = path
)

fun import(
    target: TargetNode,
    path: ImportPath = ImportPath.absolute(listOf("Module"))
) = ImportNode(
    target = target,
    path = path,
    source = anySource()
)

fun staticReference(name: String) = StaticReferenceNode(Identifier(name), anySource())
fun staticFieldAccess(
    receiver: StaticExpressionNode,
    fieldName: String
) = StaticFieldAccessNode(
    receiver = receiver,
    fieldName = Identifier(fieldName),
    source = anySource()
)
fun staticApplication(
    receiver: StaticExpressionNode,
    arguments: List<StaticExpressionNode>
) = StaticApplicationNode(
    receiver = receiver,
    arguments = arguments,
    source = anySource()
)
fun functionTypeNode(
    staticParameters: List<StaticParameterNode> = listOf(),
    positionalParameters: List<StaticExpressionNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    returnType: StaticExpressionNode,
    effects: List<StaticExpressionNode> = listOf()
) = FunctionTypeNode(
    staticParameters = staticParameters,
    positionalParameters = positionalParameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effects = effects,
    source = anySource()
)

fun tupleTypeNode(
    elementTypes: List<StaticExpressionNode>
) = TupleTypeNode(
    elementTypes = elementTypes,
    source = anySource()
)

fun typeAlias(
    name: String,
    aliasedType: Type
) = LazyTypeAlias(name = Identifier(name), getAliasedType = lazy { aliasedType })

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

fun moduleType(fields: Map<String, Type> = mapOf()) = ModuleType(
    fields = fields.mapKeys { (name, type) -> Identifier(name) }
)

fun symbolType(module: List<String>, name: String): SymbolType {
    return SymbolType(Symbol(module.map(::Identifier), name))
}

fun discriminator(symbolType: SymbolType, fieldName: String, targetType: Type = AnyType): Discriminator {
    return Discriminator(
        field = field(fieldName, symbolType, shapeId = freshShapeId()),
        symbolType = symbolType,
        targetType = targetType
    )
}

fun typesMap(
    expressionTypes: Map<Node, Type> = mapOf(),
    variableTypes: Map<VariableBindingNode, Type> = mapOf(),
    discriminators: Map<Pair<ExpressionNode, StaticExpressionNode>, Discriminator> = mapOf()
): Types {
    val types = TypesMap(
        expressionTypes = expressionTypes.mapKeys { (key, _) -> key.nodeId },
        variableTypes = variableTypes.mapKeys { (key, _) -> key.nodeId }
    )

    return object: Types by types {
        override fun findDiscriminator(expression: ExpressionNode, type: StaticExpressionNode): Discriminator {
            return discriminators[Pair(expression, type)]!!
        }
    }
}
