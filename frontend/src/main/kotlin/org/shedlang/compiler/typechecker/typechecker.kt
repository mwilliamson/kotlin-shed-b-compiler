package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*
import typeCheckStaticParameters
import java.util.*

fun newTypeContext(
    nodeTypes: Map<Int, Type> = mapOf(),
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleType
): TypeContext {
    return TypeContext(
        returnType = null,
        effect = EmptyEffect,
        nodeTypes = nodeTypes.toMutableMap(),
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        deferred = LinkedList()
    )
}

class TypeContext(
    val returnType: Type?,
    val effect: Effect,
    private val nodeTypes: MutableMap<Int, Type>,
    private val resolvedReferences: ResolvedReferences,
    private val getModule: (ImportPath) -> ModuleType,
    private val deferred: Queue<() -> Unit>
) {
    internal fun getNodeTypes(): NodeTypesMap {
        return NodeTypesMap(nodeTypes)
    }

    fun moduleType(path: ImportPath): ModuleType {
        return getModule(path)
    }

    fun typeOf(node: VariableBindingNode): Type {
        val type = nodeTypes[node.nodeId]
        if (type == null) {
            // TODO: test this
            throw CompilerError(
                "type of ${node.name} is unknown",
                source = node.source
            )
        } else {
            return type
        }
    }

    fun typeOf(reference: ReferenceNode): Type {
        val targetNodeId = resolvedReferences[reference]
        val type = nodeTypes[targetNodeId]
        if (type == null) {
            throw CompilerError(
                "type of ${reference.name} is unknown",
                source = reference.source
            )
        } else {
            return type
        }
    }

    fun addTypes(types: Map<Int, Type>) {
        nodeTypes += types
    }

    fun addType(node: VariableBindingNode, type: Type) {
        nodeTypes[node.nodeId] = type
    }

    fun addType(node: ReferenceNode, type: Type) {
        val targetNodeId = resolvedReferences[node]
        nodeTypes[targetNodeId] = type
    }

    fun enterFunction(returnType: Type?, effect: Effect): TypeContext {
        return TypeContext(
            returnType = returnType,
            effect = effect,
            nodeTypes = nodeTypes,
            resolvedReferences = resolvedReferences,
            getModule = getModule,
            deferred = deferred
        ).enterScope()
    }

    fun enterScope(): TypeContext {
        return TypeContext(
            returnType = returnType,
            effect = effect,
            nodeTypes = HashMap(nodeTypes),
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
    val types: NodeTypes,
    val moduleType: ModuleType
)

internal fun typeCheck(
    module: ModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleType
): TypeCheckResult {
    val typeContext = newTypeContext(
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule
    )
    val moduleType = typeCheck(module, typeContext)
    return TypeCheckResult(
        types = typeContext.getNodeTypes(),
        moduleType = moduleType
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

internal fun typeCheck(import: ImportNode, context: TypeContext) {
    context.addType(import, context.moduleType(import.path))
}

internal fun typeCheckFunction(function: FunctionNode, context: TypeContext, hint: Type? = null): Type {
    val staticParameters = typeCheckStaticParameters(function.staticParameters, context)

    val argumentTypes = function.arguments.map(
        { argument -> evalType(argument.type, context) }
    )
    context.addTypes(function.arguments.zip(
        argumentTypes,
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
                returnType = null,
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
                    returnType = returnType,
                    effect = effect
                )
                typeCheck(body.nodes, bodyContext)
            })

            returnType
        }
    }

    val functionType = FunctionType(
        staticParameters = staticParameters,
        positionalArguments = argumentTypes,
        namedArguments = mapOf(),
        effect = effect,
        returns = returnType
    )

    context.defer { checkReturns(function, functionType, nodeTypes = context.getNodeTypes()) }

    return functionType
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
            expected = MetaType(AnyType),
            actual = staticValue,
            source = type.source
        )
    }
}

private fun evalStatic(node: StaticNode, context: TypeContext): Type {
    return node.accept(object : StaticNode.Visitor<Type> {
        override fun visit(node: StaticReferenceNode): Type {
            return context.typeOf(node)
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
            val arguments = node.arguments.map({ argument -> evalType(argument, context) })
            if (receiver is TypeFunction) {
                return MetaType(applyType(receiver, arguments))
            } else {
                // TODO: throw a more appropriate exception
                throw CompilerError("TODO", source = node.source)
            }
        }

        override fun visit(node: FunctionTypeNode): Type {
            val staticParameters = typeCheckStaticParameters(node.staticParameters, context)
            val positionalArguments = node.arguments.map({ argument -> evalType(argument, context) })
            val effect = evalEffects(node.effects, context)
            val returnType = evalType(node.returnType, context)
            val type = FunctionType(
                staticParameters = staticParameters,
                positionalArguments = positionalArguments,
                namedArguments = mapOf(),
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
        throw CompilerError("TODO", source = node.source)
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
