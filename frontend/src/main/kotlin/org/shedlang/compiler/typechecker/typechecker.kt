package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ExpressionTypes
import org.shedlang.compiler.ExpressionTypesMap
import org.shedlang.compiler.ModuleResult
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.frontend.types.applyStatic
import org.shedlang.compiler.types.*
import java.util.*

internal fun newTypeContext(
    nodeTypes: Map<Int, Type> = mapOf(),
    expressionTypes: MutableMap<Int, Type> = mutableMapOf(),
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult
): TypeContext {
    return TypeContext(
        effect = EmptyEffect,
        expressionTypes = expressionTypes,
        variableTypes = nodeTypes.toMutableMap(),
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        deferred = LinkedList()
    )
}

internal class TypeContext(
    val effect: Effect,
    private val variableTypes: MutableMap<Int, Type>,
    private val expressionTypes: MutableMap<Int, Type>,
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

    fun addExpressionType(node: ExpressionNode, type: Type) {
        expressionTypes[node.nodeId] = type
    }

    fun enterFunction(effect: Effect): TypeContext {
        return TypeContext(
            effect = effect,
            expressionTypes = expressionTypes,
            variableTypes = variableTypes,
            resolvedReferences = resolvedReferences,
            getModule = getModule,
            deferred = deferred
        ).enterScope()
    }

    fun enterScope(): TypeContext {
        return TypeContext(
            effect = effect,
            expressionTypes = expressionTypes,
            variableTypes = HashMap(variableTypes),
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
    val expressionTypes: ExpressionTypes
)

internal fun typeCheck(
    module: ModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult
): TypeCheckResult {
    return typeCheckModule(
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        typeCheck = { context -> typeCheck(module, context)}
    )
}

internal fun typeCheck(
    module: TypesModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult
): TypeCheckResult {
    return typeCheckModule(
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        typeCheck = { context -> typeCheck(module, context)}
    )
}

private fun typeCheckModule(
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleResult,
    typeCheck: (TypeContext) -> ModuleType
): TypeCheckResult {
    val expressionTypes = mutableMapOf<Int, Type>()
    val typeContext = newTypeContext(
        nodeTypes = nodeTypes,
        expressionTypes = expressionTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule
    )
    val moduleType = typeCheck(typeContext)
    return TypeCheckResult(
        moduleType = moduleType,
        expressionTypes = ExpressionTypesMap(expressionTypes)
    )
}

internal fun typeCheck(module: ModuleNode, context: TypeContext): ModuleType {
    for (import in module.imports) {
        typeCheck(import, context)
    }

    val (typeDeclarations, otherStatements) = module.body
        .partition({ statement -> statement is TypeDeclarationNode })

    for (typeDeclaration in typeDeclarations) {
        typeCheck(typeDeclaration, context)
    }

    for (statement in otherStatements) {
        typeCheck(statement, context)
    }

    context.undefer()

    return ModuleType(fields = module.body.filterIsInstance<VariableBindingNode>().associateBy(
        { statement -> statement.name },
        { statement -> context.typeOf(statement) }
    ))
}

internal fun typeCheck(module: TypesModuleNode, context: TypeContext): ModuleType {
    for (import in module.imports) {
        typeCheck(import, context)
    }

    for (statement in module.body) {
        typeCheck(statement, context)
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
    context.addVariableType(import, type)
}

internal fun typeCheck(valType: ValTypeNode, context: TypeContext) {
    val type = evalType(valType.type, context)
    context.addVariableType(valType, type)
}

internal fun typeCheckFunction(function: FunctionNode, context: TypeContext, hint: Type? = null): Type {
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

    val returnType = when (body) {
        is FunctionBody.Expression -> {
            val bodyContext = context.enterFunction(
                effect = effect
            )
            val expressionType = inferType(body.expression, bodyContext)

            if (explicitReturnType != null) {
                verifyType(expected = explicitReturnType, actual = expressionType, source = body.expression.source)
            }
            explicitReturnType ?: expressionType
        }
        is FunctionBody.Statements -> {
            val returnType = if (explicitReturnType == null) {
                if (hint != null && hint is FunctionType) {
                    hint.returns
                } else {
                    throw MissingReturnTypeError("Could not infer return type for function", source = function.source)
                }
            } else {
                explicitReturnType
            }
            context.defer({
                val bodyContext = context.enterFunction(
                    effect = effect
                )
                val actualReturnType = typeCheck(body.nodes, bodyContext)
                val returnSource = (body.statements.lastOrNull() ?: function).source
                verifyType(expected = returnType, actual = actualReturnType, source = returnSource)
            })

            returnType
        }
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

private fun evalEffects(effectNodes: List<StaticNode>, context: TypeContext): Effect {
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

private fun typeCheck(type: StaticNode, context: TypeContext) {
    evalType(type, context)
}

internal fun evalType(type: StaticNode, context: TypeContext): Type {
    val staticValue = evalStatic(type, context)
    return when (staticValue) {
        is MetaType -> staticValue.type
        else -> throw UnexpectedTypeError(
            expected = MetaTypeGroup,
            actual = staticValue,
            source = type.source
        )
    }
}

private fun evalStaticValue(node: StaticNode, context: TypeContext): StaticValue {
    val static = evalStatic(node, context)
    return when (static) {
        is MetaType -> static.type
        is EffectType -> static.effect
        else -> throw UnexpectedTypeError(
            expected = StaticValueTypeGroup,
            actual = static,
            source = node.source
        )
    }
}

private fun evalStatic(node: StaticNode, context: TypeContext): Type {
    return node.accept(object : StaticNode.Visitor<Type> {
        override fun visit(node: StaticReferenceNode): Type {
            return inferReferenceType(node, context)
        }

        override fun visit(node: StaticFieldAccessNode): Type {
            val staticValue = evalStatic(node.receiver, context)
            // TODO: handle not a module
            // TODO: handle missing field
            return when (staticValue) {
                is ModuleType -> staticValue.fields[node.fieldName]!!
                else -> throw CompilerError("TODO", source = node.source)
            }
        }

        override fun visit(node: StaticApplicationNode): Type {
            val receiver = evalType(node.receiver, context)
            val arguments = node.arguments.map({ argument -> evalStaticValue(argument, context) })
            if (receiver is TypeFunction) {
                return MetaType(applyStatic(receiver, arguments))
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
                { parameter -> evalType(parameter.type, context)}
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
            checkType(type, source = node.source)
            return MetaType(type)
        }
    })
}

internal fun evalEffect(node: StaticNode, context: TypeContext): Effect {
    val effectType = evalStatic(node, context)
    if (effectType is EffectType) {
        return effectType.effect
    } else {
        // TODO: throw a more appropriate exception
        throw CompilerError("Was: " + effectType, source = node.source)
    }
}

internal fun verifyType(expected: Type, actual: Type, source: Source) {
    if (!canCoerce(from = actual, to = expected)) {
        throw UnexpectedTypeError(expected = expected, actual = actual, source = source)
    }
}

internal fun checkType(type: Type, source: Source) {
    val result = validateType(type)
    if (result.errors.isNotEmpty()) {
        // TODO: add more appropriate subclass
        throw TypeCheckError(result.errors.first(), source = source)
    }
}
