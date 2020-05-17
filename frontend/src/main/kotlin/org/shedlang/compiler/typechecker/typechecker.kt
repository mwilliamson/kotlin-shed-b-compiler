package org.shedlang.compiler.typechecker

import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*
import java.util.*

internal fun newTypeContext(
    moduleName: List<String>?,
    nodeTypes: Map<Int, Type> = mapOf(),
    expressionTypes: MutableMap<Int, Type> = mutableMapOf(),
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult
): TypeContext {
    return TypeContext(
        moduleName = moduleName,
        effect = EmptyEffect,
        expressionTypes = expressionTypes,
        targetTypes = mutableMapOf(),
        variableTypes = nodeTypes.toMutableMap(),
        functionTypes = mutableMapOf(),
        discriminators = mutableMapOf(),
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        deferred = LinkedList()
    )
}

internal class TypeContext(
    val moduleName: List<String>?,
    val effect: Effect,
    private val variableTypes: MutableMap<Int, Type>,
    private val functionTypes: MutableMap<Int, FunctionType>,
    private val discriminators: MutableMap<Int, Discriminator>,
    private val expressionTypes: MutableMap<Int, Type>,
    private val targetTypes: MutableMap<Int, Type>,
    private val resolvedReferences: ResolvedReferences,
    private val getModule: (ImportPath) -> ModuleResult,
    private val deferred: Queue<() -> Unit>
) {
    fun resolveReference(node: ReferenceNode): VariableBindingNode {
        return resolvedReferences[node]
    }

    fun module(path: ImportPath): ModuleResult {
        return getModule(path)
    }

    fun typeOf(node: VariableBindingNode): Type {
        val type = variableTypes[node.nodeId]
        if (type == null) {
            // TODO: test this
            throw CompilerError(
                "type of ${node.name.value} is unknown",
                source = node.source
            )
        } else {
            return type
        }
    }

    fun typeOfTarget(target: TargetNode): Type {
        val type = targetTypes[target.nodeId]
        if (type == null) {
            // TODO: test this
            throw CompilerError(
                "type of target is unknown",
                source = target.source
            )
        } else {
            return type
        }
    }

    fun addVariableTypes(types: Map<Int, Type>) {
        variableTypes += types
    }

    fun addVariableType(node: VariableBindingNode, type: Type) {
        variableTypes[node.nodeId] = type
    }

    fun addVariableType(node: ReferenceNode, type: Type) {
        val targetNode = resolvedReferences[node]
        variableTypes[targetNode.nodeId] = type
    }

    fun addFunctionType(node: FunctionNode, type: FunctionType) {
        functionTypes[node.nodeId] = type
    }

    fun addDiscriminator(node: Node, discriminator: Discriminator) {
        discriminators[node.nodeId] = discriminator
    }

    fun addExpressionType(node: ExpressionNode, type: Type) {
        expressionTypes[node.nodeId] = type
    }

    fun addStaticExpressionType(node: StaticExpressionNode, type: Type) {
        expressionTypes[node.nodeId] = type
    }

    fun addTargetType(target: TargetNode, type: Type) {
        targetTypes[target.nodeId] = type
    }

    fun enterFunction(function: FunctionNode, effect: Effect): TypeContext {
        return TypeContext(
            moduleName = moduleName,
            effect = effect,
            expressionTypes = expressionTypes,
            targetTypes = targetTypes,
            variableTypes = variableTypes,
            functionTypes = functionTypes,
            discriminators = discriminators,
            resolvedReferences = resolvedReferences,
            getModule = getModule,
            deferred = deferred
        ).enterScope()
    }

    fun enterScope(): TypeContext {
        return TypeContext(
            moduleName = moduleName,
            effect = effect,
            expressionTypes = expressionTypes,
            targetTypes = targetTypes,
            variableTypes = HashMap(variableTypes),
            functionTypes = functionTypes,
            discriminators = discriminators,
            resolvedReferences = resolvedReferences,
            getModule = getModule,
            deferred = deferred
        )
    }

    fun defer(deferred: () -> Unit) {
        this.deferred.add(deferred)
    }

    fun undefer() {
        while (this.deferred.isNotEmpty()) {
            val deferred = this.deferred.poll()
            deferred()
        }
    }

    fun toTypes(): Types {
        return TypesMap(
            discriminators = discriminators,
            expressionTypes = expressionTypes,
            functionTypes = functionTypes,
            targetTypes = targetTypes,
            variableTypes = variableTypes
        )
    }
}

interface NodeTypes {
    fun typeOf(node: VariableBindingNode): Type

    companion object {
        val empty: NodeTypes = NodeTypesMap(mapOf())
    }
}

internal class NodeTypesMap(private val nodeTypes: Map<Int, Type>) : NodeTypes {
    override fun typeOf(node: VariableBindingNode): Type {
        val type = nodeTypes[node.nodeId]
        if (type == null) {
            throw UnknownTypeError(name = node.name, source = node.source)
        } else {
            return type
        }
    }
}

data class TypeCheckResult(
    val moduleType: ModuleType,
    val types: Types
)

internal fun typeCheck(
    moduleName: List<String>?,
    module: ModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult
): TypeCheckResult {
    return typeCheckModule(
        moduleName = moduleName,
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        typeCheck = { context -> typeCheck(module, context)}
    )
}

internal fun typeCheck(
    moduleName: List<String>?,
    module: TypesModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult
): TypeCheckResult {
    return typeCheckModule(
        moduleName = moduleName,
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        typeCheck = { context -> typeCheck(module, context)}
    )
}

private fun typeCheckModule(
    moduleName: List<String>?,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult,
    typeCheck: (TypeContext) -> ModuleType
): TypeCheckResult {
    val expressionTypes = mutableMapOf<Int, Type>()
    val typeContext = newTypeContext(
        moduleName = moduleName,
        nodeTypes = nodeTypes,
        expressionTypes = expressionTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule
    )
    val moduleType = typeCheck(typeContext)
    return TypeCheckResult(
        moduleType = moduleType,
        types = typeContext.toTypes()
    )
}

internal fun typeCheck(module: ModuleNode, context: TypeContext): ModuleType {
    for (import in module.imports) {
        typeCheck(import, context)
    }

    val (typeDeclarations, otherStatements) = module.body
        .partition({ statement -> statement is TypeDeclarationNode })

    for (typeDeclaration in typeDeclarations) {
        typeCheckModuleStatement(typeDeclaration, context)
    }

    for (statement in otherStatements) {
        typeCheckModuleStatement(statement, context)
    }

    context.undefer()

    val exports = module.exports.associate { export ->
        export.name to context.typeOf(context.resolveReference(export))
    }
    return ModuleType(fields = exports)
}

internal fun typeCheck(module: TypesModuleNode, context: TypeContext): ModuleType {
    for (import in module.imports) {
        typeCheck(import, context)
    }

    for (statement in module.body) {
        typeCheckTypesModuleStatement(statement, context)
    }

    return ModuleType(fields = module.body.filterIsInstance<VariableBindingNode>().associateBy(
        { statement -> statement.name },
        { statement -> context.typeOf(statement) }
    ))
}

internal fun typeCheck(import: ImportNode, context: TypeContext) {
    val result = context.module(import.path)
    val type = when (result) {
        is ModuleResult.Found ->
            result.module.type
        is ModuleResult.NotFound ->
            throw ModuleNotFoundError(name = result.name, source = import.source)
        is ModuleResult.FoundMany ->
            throw MultipleModulesWithSameNameFoundError(name = result.name, source = import.source)
    }
    typeCheckTarget(import.target, type, context)
}

internal fun typeCheckTypesModuleStatement(statement: TypesModuleStatementNode, context: TypeContext) {
    return statement.accept(object : TypesModuleStatementNode.Visitor<Unit> {
        override fun visit(node: EffectDeclarationNode) {
            typeCheckEffectDeclaration(node, context)
        }

        override fun visit(node: ValTypeNode) {
            typeCheckValType(node, context)
        }
    })
}

private fun typeCheckEffectDeclaration(effectDeclaration: EffectDeclarationNode, context: TypeContext) {
    val effect = OpaqueEffect(name = effectDeclaration.name)
    context.addVariableType(effectDeclaration, StaticValueType(effect))
}

private fun typeCheckValType(valType: ValTypeNode, context: TypeContext) {
    val type = evalType(valType.type, context)
    context.addVariableType(valType, type)
}

internal fun typeCheckFunction(function: FunctionNode, context: TypeContext, hint: Type? = null): FunctionType {
    val staticParameters = typeCheckStaticParameters(function.staticParameters, context)

    val positionalParameterTypes = function.parameters.map(
        { argument -> evalType(argument.type, context) }
    )
    val namedParameterTypes = function.namedParameters.map(
        { argument -> evalType(argument.type, context) }
    )
    context.addVariableTypes((function.parameters + function.namedParameters).zip(
        positionalParameterTypes + namedParameterTypes,
        { argument, argumentType -> argument.nodeId to argumentType }
    ).toMap())

    val effect = evalEffects(function.effects, context)

    val body = function.body
    val returnTypeNode = function.returnType
    val explicitReturnType = if (returnTypeNode == null) {
        null
    } else {
        evalType(returnTypeNode, context)
    }

    val actualReturnType = lazy {
        val bodyContext = context.enterFunction(
            function,
            effect = effect
        )
        typeCheckBlock(body, bodyContext)
    }

    // TODO: test that inference takes precedence over hint
    val returnType = if (explicitReturnType != null) {
        explicitReturnType
    } else if (function.inferReturnType) {
        actualReturnType.value
    } else if (hint != null && hint is FunctionType) {
        hint.returns
    } else {
        throw MissingReturnTypeError("Could not infer return type for function", source = function.source)
    }

    context.defer {
        val returnSource = (body.statements.lastOrNull() ?: body).source
        verifyType(expected = returnType, actual = actualReturnType.value, source = returnSource)
    }

    return FunctionType(
        staticParameters = staticParameters,
        positionalParameters = positionalParameterTypes,
        namedParameters = function.namedParameters.zip(namedParameterTypes, { parameter, type ->
            parameter.name to type
        }).toMap(),
        effect = effect,
        returns = returnType
    )
}

private fun evalEffects(effectNodes: List<StaticExpressionNode>, context: TypeContext): Effect {
    val effects = effectNodes.map({ effect -> evalEffect(effect, context) }).toSet()
    val effect = if (effects.size == 0) {
        EmptyEffect
    } else if (effects.size == 1) {
        effects.single()
    } else {
        throw NotImplementedError()
    }
    return effect
}

private fun typeCheck(type: StaticExpressionNode, context: TypeContext) {
    evalType(type, context)
}

internal fun evalType(type: StaticExpressionNode, context: TypeContext): Type {
    val staticValue = evalStatic(type, context)
    val metaTypeValue = metaTypeToType(staticValue)
    if (metaTypeValue == null) {
        throw UnexpectedTypeError(
            expected = MetaTypeGroup,
            actual = staticValue,
            source = type.source
        )
    } else {
        return metaTypeValue
    }
}

internal fun evalStaticValue(node: StaticExpressionNode, context: TypeContext): StaticValue {
    val staticValue = evalStatic(node, context)
    if (staticValue is StaticValueType) {
        return staticValue.value
    } else {
        throw UnexpectedTypeError(
            expected = StaticValueTypeGroup,
            actual = staticValue,
            source = node.source
        )
    }
}

private fun evalStatic(node: StaticExpressionNode, context: TypeContext): Type {
    val type = node.accept(object : StaticExpressionNode.Visitor<Type> {
        override fun visit(node: ReferenceNode): Type {
            return inferReferenceType(node, context)
        }

        override fun visit(node: StaticFieldAccessNode): Type {
            val staticValue = evalStatic(node.receiver, context)
            // TODO: handle not a module
            // TODO: test handling of missing field
            return when (staticValue) {
                is ModuleType -> {
                    val field = staticValue.fields[node.fieldName]
                    if (field == null) {
                        throw NoSuchFieldError(node.fieldName, NodeSource(node))
                    } else {
                        return field
                    }
                }
                else -> throw CompilerError("TODO", source = node.source)
            }
        }

        override fun visit(node: StaticApplicationNode): Type {
            val receiver = evalStaticValue(node.receiver, context)
            val arguments = node.arguments.map({ argument -> evalStaticValue(argument, context) })
            if (receiver is ParameterizedStaticValue) {
                return StaticValueType(applyStatic(receiver, arguments))
            } else {
                // TODO: throw a more appropriate exception
                throw CompilerError("TODO", source = node.source)
            }
        }

        override fun visit(node: FunctionTypeNode): Type {
            val staticParameters = typeCheckStaticParameters(node.staticParameters, context)
            val positionalParameters = node.positionalParameters.map({ parameter ->
                evalType(parameter, context)
            })
            val namedParameters = node.namedParameters.associateBy(
                { parameter -> parameter.name },
                { parameter -> evalType(parameter.type, context) }
            )
            val effect = evalEffects(node.effects, context)
            val returnType = evalType(node.returnType, context)
            val type = FunctionType(
                staticParameters = staticParameters,
                positionalParameters = positionalParameters,
                namedParameters = namedParameters,
                returns = returnType,
                effect = effect
            )
            checkStaticValue(type, source = node.source)
            return StaticValueType(type)
        }

        override fun visit(node: TupleTypeNode): Type {
            val elementTypes = node.elementTypes.map { typeNode ->
                evalType(typeNode, context)
            }
            return StaticValueType(TupleType(elementTypes = elementTypes))
        }
    })
    context.addStaticExpressionType(node, type)
    return type
}

internal fun evalEffect(node: StaticExpressionNode, context: TypeContext): Effect {
    val effectType = evalStatic(node, context)
    if (effectType is StaticValueType) {
        val value = effectType.value
        if (value is Effect) {
            return value
        }
    }

    // TODO: throw a more appropriate exception
    throw CompilerError("Was: " + effectType, source = node.source)
}

internal fun verifyType(expected: Type, actual: Type, source: Source) {
    if (!canCoerce(from = actual, to = expected)) {
        throw UnexpectedTypeError(expected = expected, actual = actual, source = source)
    }
}

internal fun checkStaticValue(value: StaticValue, source: Source) {
    val result = validateStaticValue(value)
    if (result.errors.isNotEmpty()) {
        // TODO: add more appropriate subclass
        throw TypeCheckError(result.errors.first(), source = source)
    }
}
