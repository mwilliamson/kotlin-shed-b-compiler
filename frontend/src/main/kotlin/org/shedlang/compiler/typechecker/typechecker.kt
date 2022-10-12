package org.shedlang.compiler.typechecker

import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.frontend.ModuleResult
import org.shedlang.compiler.types.*
import java.util.*

internal fun newTypeContext(
    qualifiedPrefix: List<QualifiedNamePart>,
    nodeTypes: Map<Int, Type> = mapOf(),
    expressionTypes: MutableMap<Int, Type> = mutableMapOf(),
    resolvedReferences: ResolvedReferences,
    typeRegistry: TypeRegistry,
    getModule: (ImportPath) -> ModuleResult
): TypeContext {
    return TypeContext(
        qualifiedPrefix = qualifiedPrefix,
        effect = EmptyEffect,
        handle = null,
        expressionTypes = expressionTypes,
        targetTypes = mutableMapOf(),
        variableTypes = nodeTypes.toMutableMap(),
        refinedVariableTypes = mutableMapOf(),
        functionTypes = mutableMapOf(),
        discriminators = mutableMapOf(),
        resolvedReferences = resolvedReferences,
        typeRegistry = typeRegistry,
        getModule = getModule,
        deferred = LinkedList()
    )
}

internal class HandleTypes(val resumeValueType: Type, val stateType: Type?)

internal class TypeContext(
    private val qualifiedPrefix: List<QualifiedNamePart>,
    val effect: Effect,
    val handle: HandleTypes?,
    private val variableTypes: MutableMap<Int, Type>,
    private val refinedVariableTypes: MutableMap<Int, Type>,
    private val functionTypes: MutableMap<Int, FunctionType>,
    private val discriminators: MutableMap<Int, Discriminator>,
    private val expressionTypes: MutableMap<Int, Type>,
    private val targetTypes: MutableMap<Int, Type>,
    private val resolvedReferences: ResolvedReferences,
    private val typeRegistry: TypeRegistry,
    private val getModule: (ImportPath) -> ModuleResult,
    private val deferred: Queue<() -> Unit>
) {
    fun qualifiedNameType(name: Identifier): QualifiedName {
        return QualifiedName.type(qualifiedPrefix, name)
    }

    fun fieldType(type: Type, identifier: Identifier): Type? {
        return typeRegistry.fieldType(type, identifier)
    }

    fun resolveReference(node: ReferenceNode): VariableBindingNode {
        return resolvedReferences[node]
    }

    fun module(path: ImportPath): ModuleResult {
        return getModule(path)
    }

    fun typeOf(node: VariableBindingNode): Type {
        val refinedType = refinedVariableTypes[node.nodeId]
        if (refinedType != null) {
            return refinedType
        }

        val type = variableTypes[node.nodeId]
        if (type != null) {
            return type
        }

        // TODO: test this
        throw InternalCompilerError(
            "type of ${node.name.value} is unknown",
            source = node.source
        )
    }

    fun typeOfTarget(target: TargetNode): Type {
        val type = targetTypes[target.nodeId]
        if (type == null) {
            // TODO: test this
            throw InternalCompilerError(
                "type of target is unknown",
                source = target.source
            )
        } else {
            return type
        }
    }

    fun addVariableTypes(types: Map<Int, Type>) {
        for ((nodeId, type) in types) {
            addVariableType(nodeId, type, source = NullSource)
        }
    }

    fun addVariableType(node: VariableBindingNode, type: Type) {
        addVariableType(node.nodeId, type, source = node.source)
    }

    private fun addVariableType(nodeId: Int, type: Type, source: Source) {
        if (nodeId in variableTypes) {
            throw InternalCompilerError("variable already has type", source = source)
        } else {
            variableTypes[nodeId] = type
        }
    }

    fun refineVariableType(node: ReferenceNode, type: Type) {
        val targetNode = resolvedReferences[node]
        refinedVariableTypes[targetNode.nodeId] = type
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

    fun expressionType(node: ExpressionNode): Type? {
        return expressionTypes[node.nodeId]
    }

    fun addTypeLevelExpressionType(node: TypeLevelExpressionNode, type: Type) {
        expressionTypes[node.nodeId] = type
    }

    fun addTargetType(target: TargetNode, type: Type) {
        targetTypes[target.nodeId] = type
    }

    fun enterFunction(function: FunctionNode, effect: Effect, handle: HandleTypes?): TypeContext {
        return TypeContext(
            qualifiedPrefix = qualifiedPrefix,
            effect = effect,
            handle = handle,
            expressionTypes = expressionTypes,
            targetTypes = targetTypes,
            variableTypes = variableTypes,
            refinedVariableTypes = refinedVariableTypes,
            functionTypes = functionTypes,
            discriminators = discriminators,
            resolvedReferences = resolvedReferences,
            typeRegistry = typeRegistry,
            getModule = getModule,
            deferred = deferred
        ).enterScope()
    }

    fun enterScope(extraEffect: Effect = EmptyEffect): TypeContext {
        return TypeContext(
            qualifiedPrefix = qualifiedPrefix,
            effect = effectUnion(effect, extraEffect),
            handle = handle,
            expressionTypes = expressionTypes,
            targetTypes = targetTypes,
            variableTypes = variableTypes,
            refinedVariableTypes = HashMap(refinedVariableTypes),
            functionTypes = functionTypes,
            discriminators = discriminators,
            resolvedReferences = resolvedReferences,
            typeRegistry = typeRegistry,
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

    fun copy(): TypeContext {
        return TypeContext(
            qualifiedPrefix = qualifiedPrefix,
            effect = effect,
            handle = handle,
            variableTypes = variableTypes.toMutableMap(),
            refinedVariableTypes = refinedVariableTypes.toMutableMap(),
            functionTypes = functionTypes.toMutableMap(),
            discriminators = discriminators.toMutableMap(),
            expressionTypes = expressionTypes.toMutableMap(),
            targetTypes = targetTypes.toMutableMap(),
            resolvedReferences = resolvedReferences,
            typeRegistry = typeRegistry,
            getModule = getModule,
            deferred = LinkedList(deferred),
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
    moduleName: ModuleName,
    module: ModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    typeRegistry: TypeRegistry,
    getModule: (ImportPath) -> ModuleResult
): TypeCheckResult {
    return typeCheckModule(
        moduleName = moduleName,
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        typeRegistry = typeRegistry,
        getModule = getModule,
        typeCheck = { context -> typeCheckModule(moduleName, module, context)}
    )
}

internal fun typeCheck(
    moduleName: ModuleName,
    module: TypesModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    typeRegistry: TypeRegistry,
    getModule: (ImportPath) -> ModuleResult
): TypeCheckResult {
    return typeCheckModule(
        moduleName = moduleName,
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        typeRegistry = typeRegistry,
        getModule = getModule,
        typeCheck = { context -> typeCheckTypesModule(moduleName, module, context)}
    )
}

private fun typeCheckModule(
    moduleName: ModuleName,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    typeRegistry: TypeRegistry,
    getModule: (ImportPath) -> ModuleResult,
    typeCheck: (TypeContext) -> ModuleType
): TypeCheckResult {
    val expressionTypes = mutableMapOf<Int, Type>()
    val typeContext = newTypeContext(
        qualifiedPrefix = listOf(QualifiedNamePart.Module(moduleName)),
        nodeTypes = nodeTypes,
        expressionTypes = expressionTypes,
        resolvedReferences = resolvedReferences,
        typeRegistry = typeRegistry,
        getModule = getModule
    )
    val moduleType = typeCheck(typeContext)
    return TypeCheckResult(
        moduleType = moduleType,
        types = typeContext.toTypes()
    )
}

internal fun typeCheckModule(moduleName: ModuleName, module: ModuleNode, context: TypeContext): ModuleType {
    for (import in module.imports) {
        typeCheckImport(import, context)
    }

    val stepSets = module.body.map { statement -> typeCheckModuleStatement(statement) }

    for (phase in TypeCheckPhase.values()) {
        for (steps in stepSets) {
            steps.run(phase, context)
        }
    }

    context.undefer()

    val fields = module.exports.map { export ->
        Field(
            name = export.name,
            shapeId = module.nodeId,
            type = context.typeOf(context.resolveReference(export))
        )
    }
    return ModuleType(name = moduleName, fields = fields.associateBy { field -> field.name })
}

internal fun typeCheckTypesModule(moduleName: ModuleName, module: TypesModuleNode, context: TypeContext): ModuleType {
    for (import in module.imports) {
        typeCheckImport(import, context)
    }

    for (statement in module.body) {
        typeCheckTypesModuleStatement(statement, context)
    }

    val fields = module.body.filterIsInstance<VariableBindingNode>().map { statement ->
        Field(
            name = statement.name,
            shapeId = module.nodeId,
            type = context.typeOf(statement),
        )
    }

    return ModuleType(name = moduleName, fields = fields.associateBy { field -> field.name })
}

internal fun typeCheckImport(import: ImportNode, context: TypeContext) {
    val type = findModule(import.path, context, source = import.source)
    typeCheckTarget(import.target, type, context)
}

internal fun findModule(importPath: ImportPath, context: TypeContext, source: Source): ModuleType {
    val result = context.module(importPath)
    return when (result) {
        is ModuleResult.Found ->
            result.module.type
        is ModuleResult.NotFound ->
            throw ModuleNotFoundError(name = result.name, source = source)
        is ModuleResult.FoundMany ->
            throw MultipleModulesWithSameNameFoundError(name = result.name, source = source)
    }
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
    val effect = OpaqueEffect(
        definitionId = effectDeclaration.nodeId,
        name = effectDeclaration.name
    )
    context.addVariableType(effectDeclaration, TypeLevelValueType(effect))
}

private fun typeCheckValType(valType: ValTypeNode, context: TypeContext) {
    val type = evalType(valType.type, context)
    context.addVariableType(valType, type)
}

internal fun typeCheckFunction(
    function: FunctionNode,
    context: TypeContext,
    hint: Type? = null,
    handle: HandleTypes? = null,
    implicitEffect: Effect = EmptyEffect
): FunctionType {
    val typeLevelParameters = typeCheckTypeLevelParameters(function.typeLevelParameters, context)

    fun evalParameterType(parameter: ParameterNode, parameterHint: Type?): Type {
        val parameterType = parameter.type
        if (parameterType != null) {
            return evalType(parameterType, context)
        } else if (parameterHint != null) {
            return parameterHint
        } else {
            throw MissingParameterTypeError("Missing type for parameter ${parameter.name.value}", source = parameter.source)
        }
    }

    val positionalParameterTypes = function.parameters.mapIndexed { parameterIndex, parameter ->
        // TODO: test this more thoroughly?
        val parameterHint = if (hint != null && hint is FunctionType && parameterIndex < hint.positionalParameters.size) {
            hint.positionalParameters[parameterIndex]
        } else {
            null
        }
        evalParameterType(parameter, parameterHint = parameterHint)
    }

    val namedParameterTypes = function.namedParameters.map { parameter ->
        // TODO: test this more thoroughly?
        val parameterHint = if (hint != null && hint is FunctionType) {
            hint.namedParameters[parameter.name]
        } else {
            null
        }
        evalParameterType(parameter, parameterHint = parameterHint)
    }

    context.addVariableTypes((function.parameters + function.namedParameters).zip(
        positionalParameterTypes + namedParameterTypes,
        { argument, argumentType -> argument.nodeId to argumentType }
    ).toMap())

    val functionHint = hint as? FunctionType
    val explicitEffect = evalFunctionDefinitionEffect(function.effect, context, effectHint = functionHint?.effect)
    val effect = effectUnion(implicitEffect, explicitEffect)

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
            handle = handle,
            effect = effect
        )
        typeCheckBlock(body, bodyContext)
    }

    // TODO: test that inference takes precedence over hint
    val returnType = if (explicitReturnType != null) {
        explicitReturnType
    } else if (function.inferReturnType) {
        actualReturnType.value
    } else if (functionHint != null) {
        hint.returns
    } else {
        throw MissingReturnTypeError("Could not infer return type for function", source = function.source)
    }

    context.defer {
        val returnSource = (body.statements.lastOrNull() ?: body).source
        verifyType(expected = returnType, actual = actualReturnType.value, source = returnSource)
    }

    return FunctionType(
        typeLevelParameters = typeLevelParameters,
        positionalParameters = positionalParameterTypes,
        namedParameters = function.namedParameters.zip(namedParameterTypes, { parameter, type ->
            parameter.name to type
        }).toMap(),
        effect = effect,
        returns = returnType
    )
}

private fun evalFunctionDefinitionEffect(node: FunctionEffectNode?, context: TypeContext, effectHint: Effect?): Effect {
    return when (node) {
        is FunctionEffectNode.Infer ->
            if (effectHint == null) {
                // TODO: better error
                throw SourceError("cannot infer effect, no hint available", source = node.source)
            } else {
                return effectHint
            }

        is FunctionEffectNode.Explicit ->
            evalEffect(node.expression, context)

        null ->
            evalEffect(node, context)
    }
}

internal fun evalType(type: TypeLevelExpressionNode, context: TypeContext): Type {
    val typeLevelValue = evalTypeLevel(type, context)
    val metaTypeValue = metaTypeToType(typeLevelValue)
    if (metaTypeValue == null) {
        throw UnexpectedTypeError(
            expected = MetaTypeGroup,
            actual = typeLevelValue,
            source = type.source
        )
    } else {
        return metaTypeValue
    }
}

internal fun evalTypeLevelValue(node: TypeLevelExpressionNode, context: TypeContext): TypeLevelValue {
    val typeLevelValue = evalTypeLevel(node, context)
    if (typeLevelValue is TypeLevelValueType) {
        return typeLevelValue.value
    } else {
        throw UnexpectedTypeError(
            expected = TypeLevelValueTypeGroup,
            actual = typeLevelValue,
            source = node.source
        )
    }
}

private fun evalTypeLevel(node: TypeLevelExpressionNode, context: TypeContext): Type {
    val type = node.accept(object : TypeLevelExpressionNode.Visitor<Type> {
        override fun visit(node: ReferenceNode): Type {
            return inferReferenceType(node, context)
        }

        override fun visit(node: TypeLevelFieldAccessNode): Type {
            val typeLevelValue = evalTypeLevel(node.receiver, context)
            return inferFieldAccessType(typeLevelValue, node.fieldName, context)
        }

        override fun visit(node: TypeLevelApplicationNode): Type {
            val receiver = evalTypeLevelValue(node.receiver, context)
            if (receiver is TypeConstructor) {
                val arguments = node.arguments.map({ argument -> evalTypeLevelValue(argument, context) })
                // TODO: check parameters and arguments match (size)
                return TypeLevelValueType(applyTypeLevel(receiver, arguments, source = node.operatorSource))
            } else if (receiver is CastableTypeLevelFunction) {
                // TODO: Test this
                // TODO: proper error handling
                // TODO: restrict valid types? e.g. is it meaningful for Any?
                return metaType(CastableType(evalType(node.arguments.single(), context)))
            } else if (receiver is MetaTypeTypeLevelFunction) {
                // TODO: Test this
                // TODO: proper error handling
                // TODO: restrict valid types? e.g. is it meaningful for Any?
                return metaType(evalTypeLevel(node.arguments.single(), context))
            } else {
                // TODO: throw a more appropriate exception
                throw InternalCompilerError("TODO", source = node.operatorSource)
            }
        }

        override fun visit(node: FunctionTypeNode): Type {
            val typeLevelParameters = typeCheckTypeLevelParameters(node.typeLevelParameters, context)
            val positionalParameters = node.positionalParameters.map({ parameter ->
                evalType(parameter, context)
            })
            val namedParameters = node.namedParameters.associateBy(
                { parameter -> parameter.name },
                { parameter -> evalType(parameter.type, context) }
            )
            val effect = evalEffect(node.effect, context)
            val returnType = evalType(node.returnType, context)
            val type = FunctionType(
                typeLevelParameters = typeLevelParameters,
                positionalParameters = positionalParameters,
                namedParameters = namedParameters,
                returns = returnType,
                effect = effect
            )
            checkTypeLevelValue(type, source = node.source)
            return TypeLevelValueType(type)
        }

        override fun visit(node: TupleTypeNode): Type {
            val elementTypes = node.elementTypes.map { typeNode ->
                evalType(typeNode, context)
            }
            return TypeLevelValueType(TupleType(elementTypes = elementTypes))
        }

        override fun visit(node: TypeLevelUnionNode): Type {
            val effects = node.elements.map { element -> evalEffect(element, context) }
            return TypeLevelValueType(effectUnion(effects))
        }
    })
    context.addTypeLevelExpressionType(node, type)
    return type
}

internal fun evalEffect(node: TypeLevelExpressionNode?, context: TypeContext): Effect {
    if (node == null) {
        return EmptyEffect
    }

    val effectType = evalTypeLevel(node, context)
    if (effectType is TypeLevelValueType) {
        val value = effectType.value
        if (value is Effect) {
            return value
        }
    }

    // TODO: throw a more appropriate exception
    throw InternalCompilerError("Was: " + effectType, source = node.source)
}

internal fun verifyType(expected: Type, actual: Type, source: Source) {
    if (!canCoerce(from = actual, to = expected)) {
        canCoerce(from = actual, to = expected)
        throw UnexpectedTypeError(expected = expected, actual = actual, source = source)
    }
}

internal fun checkTypeLevelValue(value: TypeLevelValue, source: Source) {
    val result = validateTypeLevelValue(value)
    if (result.errors.isNotEmpty()) {
        // TODO: add more appropriate subclass
        throw TypeCheckError(result.errors.first(), source = source)
    }
}
