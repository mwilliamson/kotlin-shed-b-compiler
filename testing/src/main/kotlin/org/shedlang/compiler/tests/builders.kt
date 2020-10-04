package org.shedlang.compiler.tests

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.FieldInspector
import org.shedlang.compiler.backends.FieldValue
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
        type = ExpressionStatementNode.Type.NO_VALUE,
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
        elseBranch = Block(elseBranch, source = anySource()),
        source = anySource()
    )
}

fun conditionalBranch(
    condition: ExpressionNode,
    body: List<FunctionStatementNode>
) = ConditionalBranchNode(
    condition = condition,
    body = Block(body, source = anySource()),
    source = anySource()
)

fun whenExpression(
    expression: ExpressionNode,
    conditionalBranches: List<WhenBranchNode> = listOf(),
    elseBranch: List<FunctionStatementNode>? = null
) = WhenNode(
    expression = expression,
    conditionalBranches = conditionalBranches,
    elseBranch = if (elseBranch == null) null else Block(elseBranch, source = anySource()),
    source = anySource()
)

fun whenBranch(
    type: StaticExpressionNode = staticReference("__default__"),
    target: TargetNode.Fields = targetFields(),
    body: List<FunctionStatementNode> = listOf()
) = WhenBranchNode(
    type = type,
    target = target,
    body = Block(body, source = anySource()),
    source = anySource()
)

fun expressionStatementNoReturn(
    expression: ExpressionNode = expression(),
    source: Source = anySource()
) = ExpressionStatementNode(
    expression = expression,
    type = ExpressionStatementNode.Type.NO_VALUE,
    source = source
)

fun expressionStatementReturn(
    expression: ExpressionNode = expression(),
    source: Source = anySource()
) = ExpressionStatementNode(
    expression = expression,
    type = ExpressionStatementNode.Type.VALUE,
    source = source
)

fun expressionStatementTailRecReturn(
    expression: ExpressionNode = expression(),
    source: Source = anySource()
) = ExpressionStatementNode(
    expression = expression,
    type = ExpressionStatementNode.Type.TAILREC,
    source = source
)

fun exit(
    expression: ExpressionNode = expression(),
    source: Source = anySource()
) = ExpressionStatementNode(
    expression = expression,
    type = ExpressionStatementNode.Type.EXIT,
    source = source
)

fun resume(
    expression: ExpressionNode = expression(),
    source: Source = anySource()
) = ExpressionStatementNode(
    expression = expression,
    type = ExpressionStatementNode.Type.RESUME,
    source = source
)

fun expressionStatement(
    expression: ExpressionNode = expression(),
    source: Source = anySource()
) = ExpressionStatementNode(
    expression,
    type = ExpressionStatementNode.Type.NO_VALUE,
    source = source
)

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

fun targetIgnore() = TargetNode.Ignore(source = anySource())

fun targetVariable(name: String = "<target name>") = TargetNode.Variable(
    Identifier(name),
    source = anySource()
)

fun targetTuple(elements: List<TargetNode>) = TargetNode.Tuple(
    elements,
    source = anySource()
)

fun targetFields(fields: List<Pair<FieldNameNode, TargetNode>> = listOf()) = TargetNode.Fields(
    fields,
    source = anySource()
)

fun variableBinder(
    name: String
): VariableBindingNode = parameter(name = name)

fun effectDeclaration(
    name: String
) = EffectDeclarationNode(
    name = Identifier(name),
    source = anySource()
)

fun valType(
    name: String = "<val name>",
    type: StaticExpressionNode
) = ValTypeNode(
    name = Identifier(name),
    type = type,
    source = anySource()
)

fun block(statements: List<FunctionStatementNode>) = Block(
    statements,
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
fun literalUnicodeScalar(value: Char = '!') = UnicodeScalarLiteralNode(value.toInt(), anySource())
fun tupleNode(elements: List<ExpressionNode>) = TupleNode(elements, anySource())
fun variableReference(name: String) = ReferenceNode(Identifier(name), anySource())

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
    source = anySource(),
    operatorSource = anySource(),
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
    source = anySource(),
    operatorSource = anySource(),
)

fun callNamedArgument(
    name: String,
    expression: ExpressionNode
) = CallNamedArgumentNode(
    name = Identifier(name),
    expression = expression,
    source = anySource()
)

fun staticCall(
    receiver: ExpressionNode,
    arguments: List<StaticExpressionNode> = listOf(),
) = StaticCallNode(
    receiver = receiver,
    arguments = arguments,
    source = anySource(),
    operatorSource = anySource(),
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

fun handle(
    effect: StaticExpressionNode = staticReference("Eff"),
    initialState: ExpressionNode? = null,
    body: Block,
    handlers: List<HandlerNode>
) = HandleNode(
    effect = effect,
    initialState = initialState,
    body = body,
    handlers = handlers,
    source = anySource()
)

fun handler(
    operationName: String,
    function: FunctionExpressionNode
) = HandlerNode(
    operationName = Identifier(operationName),
    function = function,
    source = anySource()
)

fun function(
    name: String = "f",
    staticParameters: List<StaticParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effect: StaticExpressionNode? = null,
    returnType: StaticExpressionNode = staticReference("Unit"),
    body: List<FunctionStatementNode> = listOf(),
    inferReturnType: Boolean = false
) = FunctionDefinitionNode(
    name = Identifier(name),
    staticParameters = staticParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effect = effect,
    body = Block(body, source = anySource()),
    inferReturnType = inferReturnType,
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effect: StaticExpressionNode? = null,
    returnType: StaticExpressionNode? = null,
    body: List<FunctionStatementNode> = listOf(),
    inferReturnType: Boolean = false
) = FunctionExpressionNode(
    staticParameters = typeParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effect = effect,
    body = Block(body, source = anySource()),
    inferReturnType = inferReturnType,
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effect: StaticExpressionNode? = null,
    returnType: StaticExpressionNode? = staticReference("Unit"),
    body: ExpressionNode,
    inferReturnType: Boolean = true
) = functionExpression(
    typeParameters = typeParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effect = effect,
    body = listOf(
        ExpressionStatementNode(body, type = ExpressionStatementNode.Type.VALUE, source = body.source)
    ),
    inferReturnType = inferReturnType
)

fun effectDefinition(
    name: String,
    operations: List<OperationDefinitionNode>,
    nodeId: Int = freshNodeId()
) = EffectDefinitionNode(
    name = Identifier(name),
    operations = operations,
    source = anySource(),
    nodeId = nodeId
)

fun operationDefinition(
    name: String,
    type: FunctionTypeNode
) = OperationDefinitionNode(
    name = Identifier(name),
    type = type,
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
    superType: ReferenceNode? = null
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

fun varargsDeclaration(
    name: String,
    cons: ExpressionNode,
    nil: ExpressionNode
) = VarargsDeclarationNode(
    name = Identifier(name),
    cons = cons,
    nil = nil,
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
    exports: List<ReferenceNode> = listOf()
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

fun export(name: String) = ReferenceNode(
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

fun staticReference(name: String) = ReferenceNode(Identifier(name), anySource())
fun staticFieldAccess(
    receiver: StaticExpressionNode,
    fieldName: String
) = StaticFieldAccessNode(
    receiver = receiver,
    fieldName = fieldName(fieldName),
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
    returnType: StaticExpressionNode = staticReference("Unit"),
    effect: StaticExpressionNode? = null
) = FunctionTypeNode(
    staticParameters = staticParameters,
    positionalParameters = positionalParameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effect = effect,
    source = anySource()
)

fun tupleTypeNode(
    elementTypes: List<StaticExpressionNode>
) = TupleTypeNode(
    elementTypes = elementTypes,
    source = anySource()
)

fun staticUnion(
    elements: List<StaticExpressionNode>
) = StaticUnionNode(
    elements = elements,
    source = anySource()
)

fun typeAlias(
    name: String,
    aliasedType: Type
) = LazyTypeAlias(name = Identifier(name), getAliasedType = lazy { aliasedType })

fun parametrizedShapeType(
    name: String,
    parameters: List<TypeParameter>,
    tagValue: TagValue? = null,
    fields: List<Field> = listOf()
) = ParameterizedStaticValue(
    value = shapeType(
        name = name,
        tagValue = tagValue,
        fields = fields,
        typeParameters = parameters,
        typeArguments = parameters
    ),
    parameters = parameters
)

fun shapeType(
    name: String = "Shape",
    fields: List<Field> = listOf(),
    tagValue: TagValue? = null,
    typeParameters: List<TypeParameter> = listOf(),
    typeArguments: List<Type> = listOf(),
    shapeId: Int = freshTypeId()
) = lazyCompleteShapeType(
    shapeId = shapeId,
    name = Identifier(name),
    tagValue = tagValue,
    getFields = lazy { fields },
    staticParameters = typeParameters,
    staticArguments = typeArguments
)

fun field(name: String, type: Type, isConstant: Boolean = false, shapeId: Int = freshTypeId()) = Field(
    shapeId,
    Identifier(name),
    type,
    isConstant = isConstant
)

fun fieldInspector(name: String, value: FieldValue? = null) = FieldInspector(
    name = Identifier(name),
    value = value,
    source = anySource()
)

fun parametrizedUnionType(
    name: String,
    tag: Tag = Tag(listOf(), Identifier(name)),
    parameters: List<TypeParameter> = listOf(invariantTypeParameter("T")),
    members: List<ShapeType> = listOf()
) = ParameterizedStaticValue(
    value = LazyUnionType(
        name = Identifier(name),
        tag = tag,
        getMembers = lazy { members },
        staticArguments = parameters
    ),
    parameters = parameters
)

fun tag(moduleName: List<String>, name: String) = Tag(moduleName.map(::Identifier), Identifier(name))

fun tagValue(tag: Tag, value: String) = TagValue(tag, Identifier(value))

fun unionType(
    name: String = "Union",
    tag: Tag = Tag(listOf(), Identifier(name)),
    members: List<Type> = listOf()
) = LazyUnionType(
    name = Identifier(name),
    tag = tag,
    getMembers = lazy { members },
    staticArguments = listOf()
)

fun varargsType() = VarargsType(
    name = Identifier("Varargs"),
    cons = functionType(),
    nil = UnitType
)

fun moduleType(fields: Map<String, Type> = mapOf()) = ModuleType(
    fields = fields.mapKeys { (name, type) -> Identifier(name) }
)

fun discriminator(tagValue: TagValue, targetType: Type = AnyType): Discriminator {
    return Discriminator(
        tagValue = tagValue,
        targetType = targetType
    )
}

fun userDefinedEffect(
    name: Identifier,
    getOperations: (UserDefinedEffect) -> Map<Identifier, FunctionType> = { mapOf() }
): UserDefinedEffect {
    var effect: UserDefinedEffect? = null
    effect = userDefinedEffect(
        name = name,
        getOperations = lazy {
            getOperations(effect!!)
        }
    )
    return effect
}

fun userDefinedEffect(
    name: Identifier,
    getOperations: Lazy<Map<Identifier, FunctionType>>
) = UserDefinedEffect(
    definitionId = freshNodeId(),
    name = name,
    getOperations = getOperations
)

fun opaqueEffect(name: String) = OpaqueEffect(
    definitionId = freshNodeId(),
    name = Identifier(name)
)
