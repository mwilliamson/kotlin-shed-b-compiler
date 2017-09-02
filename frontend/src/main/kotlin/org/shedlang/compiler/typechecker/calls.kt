package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

internal fun inferType(node: CallNode, context: TypeContext): Type {
    val receiverType = inferType(node.receiver, context)

    for ((name, arguments) in node.namedArguments.groupBy(CallNamedArgumentNode::name)) {
        if (arguments.size > 1) {
            throw ArgumentAlreadyPassedError(name, source = arguments[1].source)
        }
    }

    if (receiverType is FunctionType) {
        return inferFunctionCallType(node, receiverType, context)
    } else if (receiverType is MetaType && receiverType.type is ShapeType) {
        val shapeType = receiverType.type
        return inferConstructorCallType(node, null, shapeType, context)
    } else if (receiverType is MetaType && receiverType.type is TypeFunction && receiverType.type.type is ShapeType) {
        return inferConstructorCallType(node, receiverType.type, receiverType.type.type, context)
    } else {
        val argumentTypes = node.positionalArguments.map { argument -> inferType(argument, context) }
        throw UnexpectedTypeError(
            expected = FunctionType(
                staticParameters = listOf(),
                positionalArguments = argumentTypes,
                namedArguments = mapOf(),
                returns = AnyType,
                effects = setOf()
            ),
            actual = receiverType,
            source = node.receiver.source
        )
    }
}

private fun inferFunctionCallType(
    node: CallNode,
    receiverType: FunctionType,
    context: TypeContext
): Type {
    val typeParameterBindings = checkArguments(
        call = node,
        staticParameters = receiverType.staticParameters,
        positionalParameters = receiverType.positionalArguments,
        namedParameters = receiverType.namedArguments,
        context = context
    )

    val unhandledEffects = receiverType.effects - context.effects
    if (unhandledEffects.isNotEmpty()) {
        throw UnhandledEffectError(unhandledEffects.first(), source = node.source)
    }

    // TODO: handle unconstrained types
    return replaceTypes(receiverType.returns, typeParameterBindings)
}

private fun inferConstructorCallType(
    node: CallNode,
    typeFunction: TypeFunction?,
    shapeType: ShapeType,
    context: TypeContext
): Type {
    if (node.positionalArguments.any()) {
        throw PositionalArgumentPassedToShapeConstructorError(source = node.positionalArguments.first().source)
    }

    val typeParameterBindings = checkArguments(
        call = node,
        staticParameters = typeFunction?.parameters ?: listOf(),
        positionalParameters = listOf(),
        namedParameters = shapeType.fields,
        context = context
    )

    if (typeFunction == null) {
        return shapeType
    } else {
        return applyType(typeFunction, typeFunction.parameters.map({ parameter ->
            typeParameterBindings[parameter]!!
        }))
    }
}

private fun checkArguments(
    call: CallNode,
    staticParameters: List<StaticParameter>,
    positionalParameters: List<Type>,
    namedParameters: Map<String, Type>,
    context: TypeContext
): Map<TypeParameter, Type> {
    val positionalArguments = call.positionalArguments.zip(positionalParameters)
    if (positionalParameters.size != call.positionalArguments.size) {
        throw WrongNumberOfArgumentsError(
            expected = positionalParameters.size,
            actual = call.positionalArguments.size,
            source = call.source
        )
    }

    val namedArguments = call.namedArguments.map({ argument ->
        val fieldType = namedParameters[argument.name]
        if (fieldType == null) {
            throw ExtraArgumentError(argument.name, source = argument.source)
        } else {
            argument.expression to fieldType
        }
    })

    val missingNamedArguments = namedParameters.keys - call.namedArguments.map({ argument -> argument.name })
    for (missingNamedArgument in missingNamedArguments) {
        throw MissingArgumentError(missingNamedArgument, source = call.source)
    }

    val arguments = positionalArguments + namedArguments
    return checkArgumentTypes(
        staticParameters = staticParameters,
        staticArguments = call.typeArguments,
        arguments = arguments,
        source = call.source,
        context = context
    )
}

private fun checkArgumentTypes(
    staticParameters: List<StaticParameter>,
    staticArguments: List<StaticNode>,
    arguments: List<Pair<ExpressionNode, Type>>,
    source: Source,
    context: TypeContext
): Map<TypeParameter, Type> {
    if (staticArguments.isEmpty()) {
        val typeParameters = staticParameters.filterIsInstance<TypeParameter>()
        val inferredTypeArguments = typeParameters.map({ parameter -> TypeParameter(
            name = parameter.name,
            variance = parameter.variance
        ) })

        val constraints = TypeConstraintSolver(
            parameters = inferredTypeArguments.toMutableSet()
        )
        for (argument in arguments) {
            val actualType = inferType(argument.first, context)
            val formalType = replaceTypes(
                argument.second,
                typeParameters.zip(inferredTypeArguments).toMap()
            )
            if (!constraints.coerce(from = actualType, to = formalType)) {
                throw UnexpectedTypeError(
                    expected = formalType,
                    actual = actualType,
                    source = argument.first.source
                )
            }
        }
        return typeParameters.zip(inferredTypeArguments)
            .associate({ (parameter, inferredArgument) ->
                val boundType = constraints.bindings[inferredArgument]
                parameter to if (boundType != null) {
                    boundType
                } else if (parameter.variance == Variance.COVARIANT) {
                    NothingType
                } else if (parameter.variance == Variance.CONTRAVARIANT) {
                    AnyType
                } else {
                    throw CouldNotInferTypeParameterError(
                        parameter = parameter,
                        source = source
                    )
                }
            })
    } else {
        if (staticArguments.size != staticParameters.size) {
            throw WrongNumberOfStaticArgumentsError(
                expected = staticParameters.size,
                actual = staticArguments.size,
                source = source
            )
        }

        val typeMap = staticParameters.zip(staticArguments)
            .filterIsInstance<Pair<TypeParameter, StaticNode>>()
            .map({ (typeParameter, typeArgument) ->
                typeParameter to evalType(typeArgument, context)
            })
            .toMap()

        checkArgumentTypes(
            staticParameters = listOf(),
            staticArguments = listOf(),
            arguments = arguments.map({ (expression, type) ->
                expression to replaceTypes(type, typeMap)
            }),
            source = source,
            context = context
        )

        return typeMap
    }
}
