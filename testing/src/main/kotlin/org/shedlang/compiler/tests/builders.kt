package org.shedlang.compiler.tests

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
        elseBranch = BlockNode(elseBranch, source = anySource()),
        source = anySource()
    )
}

fun conditionalBranch(
    condition: ExpressionNode,
    body: List<FunctionStatementNode>
) = ConditionalBranchNode(
    condition = condition,
    body = BlockNode(body, source = anySource()),
    source = anySource()
)

fun whenExpression(
    expression: ExpressionNode,
    conditionalBranches: List<WhenBranchNode> = listOf(),
    elseBranch: List<FunctionStatementNode>? = null
) = WhenNode(
    expression = expression,
    conditionalBranches = conditionalBranches,
    elseBranch = if (elseBranch == null) null else BlockNode(elseBranch, source = anySource()),
    source = anySource(),
    elseSource = anySource(),
)

fun whenBranch(
    type: TypeLevelExpressionNode = typeLevelReference("__default__"),
    target: TargetNode.Fields = targetFields(),
    body: List<FunctionStatementNode> = listOf()
) = WhenBranchNode(
    type = type,
    target = target,
    body = BlockNode(body, source = anySource()),
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
    newState: ExpressionNode? = null,
    source: Source = anySource()
) = ResumeNode(
    expression = expression,
    newState = newState,
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
    type: TypeLevelExpressionNode
) = ValTypeNode(
    name = Identifier(name),
    type = type,
    source = anySource()
)

fun block(statements: List<FunctionStatementNode>) = BlockNode(
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
    type: TypeLevelExpressionNode
) = IsNode(
    expression = expression,
    type = type,
    source = anySource()
)

fun call(
    receiver: ExpressionNode,
    positionalArguments: List<ExpressionNode> = listOf(),
    namedArguments: List<FieldArgumentNode> = listOf(),
    typeLevelArguments: List<TypeLevelExpressionNode> = listOf(),
    hasEffect: Boolean = false
) = CallNode(
    receiver = receiver,
    typeLevelArguments = typeLevelArguments,
    positionalArguments = positionalArguments,
    fieldArguments = namedArguments,
    hasEffect = hasEffect,
    source = anySource(),
    operatorSource = anySource(),
)

fun partialCall(
    receiver: ExpressionNode,
    positionalArguments: List<ExpressionNode> = listOf(),
    namedArguments: List<FieldArgumentNode> = listOf(),
    typeLevelArguments: List<TypeLevelExpressionNode> = listOf()
) = PartialCallNode(
    receiver = receiver,
    typeLevelArguments = typeLevelArguments,
    positionalArguments = positionalArguments,
    fieldArguments = namedArguments,
    source = anySource(),
    operatorSource = anySource(),
)

fun callNamedArgument(
    name: String,
    expression: ExpressionNode
) = FieldArgumentNode.Named(
    name = Identifier(name),
    expression = expression,
    source = anySource()
)

fun splatArgument(expression: ExpressionNode) = FieldArgumentNode.Splat(
    expression = expression,
    source = anySource(),
)

fun typeLevelCall(
    receiver: ExpressionNode,
    arguments: List<TypeLevelExpressionNode> = listOf(),
) = TypeLevelCallNode(
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
    effect: TypeLevelExpressionNode = typeLevelReference("Eff"),
    initialState: ExpressionNode? = null,
    body: BlockNode,
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
    typeLevelParameters: List<TypeLevelParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effect: TypeLevelExpressionNode? = null,
    returnType: TypeLevelExpressionNode = typeLevelReference("Unit"),
    body: List<FunctionStatementNode> = listOf(),
    inferReturnType: Boolean = false
) = FunctionDefinitionNode(
    name = Identifier(name),
    typeLevelParameters = typeLevelParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effect = if (effect == null) null else FunctionEffectNode.Explicit(effect, source = anySource()),
    body = BlockNode(body, source = anySource()),
    inferReturnType = inferReturnType,
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effect: FunctionEffectNode? = null,
    returnType: TypeLevelExpressionNode? = null,
    body: List<FunctionStatementNode> = listOf(),
    inferReturnType: Boolean = false
) = FunctionExpressionNode(
    typeLevelParameters = typeParameters,
    parameters = parameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effect = effect,
    body = BlockNode(body, source = anySource()),
    inferReturnType = inferReturnType,
    source = anySource()
)

fun functionExpression(
    typeParameters: List<TypeParameterNode> = listOf(),
    parameters: List<ParameterNode> = listOf(),
    namedParameters: List<ParameterNode> = listOf(),
    effect: FunctionEffectNode? = null,
    returnType: TypeLevelExpressionNode? = typeLevelReference("Unit"),
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

fun functionEffectInfer() = FunctionEffectNode.Infer(source = anySource())

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
    expression: TypeLevelExpressionNode
) = TypeAliasNode(
    name = Identifier(name),
    expression = expression,
    source = anySource()
)

fun shape(
    name: String = "Shape",
    typeLevelParameters: List<TypeLevelParameterNode> = listOf(),
    extends: List<TypeLevelExpressionNode> = listOf(),
    fields: List<ShapeFieldNode> = listOf()
) = ShapeNode(
    name = Identifier(name),
    typeLevelParameters = typeLevelParameters,
    extends = extends,
    fields = fields,
    source = anySource()
)

fun shapeField(
    name: String = "field",
    type: TypeLevelExpressionNode = typeLevelReference("SomeType"),
    shape: TypeLevelExpressionNode? = null
) = ShapeFieldNode(
    shape = shape,
    name = Identifier(name),
    type = type,
    source = anySource()
)

fun union(
    name: String = "Union",
    members: List<UnionMemberNode> = listOf(),
    typeLevelParameters: List<TypeLevelParameterNode> = listOf(),
    superType: ReferenceNode? = null
) = UnionNode(
    name = Identifier(name),
    typeLevelParameters = typeLevelParameters,
    superType = superType,
    members = members,
    source = anySource()
)

fun unionMember(
    name: String,
    typeLevelParameters: List<TypeLevelParameterNode> = listOf(),
    extends: List<TypeLevelExpressionNode> = listOf(),
    fields: List<ShapeFieldNode> = listOf()
) = UnionMemberNode(
    name = Identifier(name),
    typeLevelParameters = typeLevelParameters,
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
    type: TypeLevelExpressionNode? = typeLevelReference("Int")
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

fun typeLevelReference(name: String) = ReferenceNode(Identifier(name), anySource())
fun typeLevelFieldAccess(
    receiver: TypeLevelExpressionNode,
    fieldName: String
) = TypeLevelFieldAccessNode(
    receiver = receiver,
    fieldName = fieldName(fieldName),
    source = anySource()
)
fun typeLevelApplication(
    receiver: TypeLevelExpressionNode,
    arguments: List<TypeLevelExpressionNode>
) = TypeLevelApplicationNode(
    receiver = receiver,
    arguments = arguments,
    source = anySource(),
    operatorSource = anySource(),
)
fun functionTypeNode(
    typeLevelParameters: List<TypeLevelParameterNode> = listOf(),
    positionalParameters: List<TypeLevelExpressionNode> = listOf(),
    namedParameters: List<FunctionTypeNamedParameterNode> = listOf(),
    returnType: TypeLevelExpressionNode = typeLevelReference("Unit"),
    effect: TypeLevelExpressionNode? = null
) = FunctionTypeNode(
    typeLevelParameters = typeLevelParameters,
    positionalParameters = positionalParameters,
    namedParameters = namedParameters,
    returnType = returnType,
    effect = effect,
    source = anySource()
)

fun functionTypeNamedParameter(
    name: String = "x",
    type: TypeLevelExpressionNode
) = FunctionTypeNamedParameterNode(
    name = Identifier(name),
    type = type,
    source = anySource(),
)

fun tupleTypeNode(
    elementTypes: List<TypeLevelExpressionNode>
) = TupleTypeNode(
    elementTypes = elementTypes,
    source = anySource()
)

fun typeLevelUnion(
    elements: List<TypeLevelExpressionNode>
) = TypeLevelUnionNode(
    elements = elements,
    source = anySource()
)

fun typeAlias(
    name: String,
    aliasedType: Type
) = LazyTypeAlias(name = Identifier(name), getAliasedType = lazy { aliasedType })

fun parametrizedShapeType(
    name: String = "Shape",
    parameters: List<TypeLevelParameter>,
    tagValue: TagValue? = null,
    fields: List<Field> = listOf()
) = TypeConstructor(
    genericType = shapeType(
        name = name,
        tagValue = tagValue,
        fields = fields,
    ),
    parameters = parameters
)

fun shapeType(
    name: String = "Shape",
    fields: List<Field> = listOf(),
    tagValue: TagValue? = null,
    shapeId: Int = freshTypeId()
) = lazyShapeType(
    shapeId = shapeId,
    qualifiedName = QualifiedName.builtin(name),
    tagValue = tagValue,
    getFields = lazy { fields },
)

fun field(name: String, type: Type, shapeId: Int = freshTypeId()) = Field(
    shapeId,
    Identifier(name),
    type,
)

fun parametrizedUnionType(
    name: String,
    tag: Tag = tag(name = name),
    parameters: List<TypeLevelParameter> = listOf(invariantTypeParameter("T")),
    members: List<Type> = listOf()
) = TypeConstructor(
    genericType = LazySimpleUnionType(
        name = Identifier(name),
        tag = tag,
        getMembers = lazy { members },
    ),
    parameters = parameters
)

fun tag(moduleName: List<String> = listOf("Example"), name: String) = Tag(QualifiedName.topLevelType(moduleName, name))

fun tagValue(tag: Tag, value: String) = TagValue(tag, Identifier(value))

fun unionType(
    name: String = "Union",
    tag: Tag = tag(name = name),
    members: List<Type> = listOf()
) = LazySimpleUnionType(
    name = Identifier(name),
    tag = tag,
    getMembers = lazy { members },
)

fun varargsType() = VarargsType(
    qualifiedName = QualifiedName.builtin("Varargs"),
    cons = functionType(),
    nil = UnitType
)

fun moduleType(fields: Map<String, Type> = mapOf()): ModuleType {
    val shapeId = freshNodeId()
    return ModuleType(
        name = moduleName(),
        fields = fields.map { (name, type) ->
            Field(
                name = Identifier(name),
                type = type,
                shapeId = shapeId,
            )
        }.associateBy { field -> field.name }
    )
}


fun moduleName(
    parts: List<String> = listOf("Test", "Module"),
) = parts.map(::Identifier)

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
