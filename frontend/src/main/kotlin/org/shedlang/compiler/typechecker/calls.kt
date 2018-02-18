package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.frontend.types.StaticBindings
import org.shedlang.compiler.frontend.types.applyStatic
import org.shedlang.compiler.frontend.types.replaceEffects
import org.shedlang.compiler.frontend.types.replaceStaticValuesInType
import org.shedlang.compiler.types.*

internal fun inferCallType(node: CallNode, context: TypeContext): Type {
    val receiverType = inferType(node.receiver, context)
    val type = tryInferCallType(node, receiverType, context)
    if (type == null) {
        val argumentTypes = node.positionalArguments.map { argument -> inferType(argument, context) }
        throw UnexpectedTypeError(
            expected = FunctionType(
                staticParameters = listOf(),
                positionalParameters = argumentTypes,
                namedParameters = mapOf(),
                returns = AnyType,
                effect = EmptyEffect
            ),
            actual = receiverType,
            source = node.receiver.source
        )
    } else {
        return type
    }
}

internal fun tryInferCallType(node: CallNode, receiverType: Type, context: TypeContext): Type? {
    if (receiverType is FunctionType) {
        return inferFunctionCallType(node, receiverType, context)
    } else if (receiverType is MetaType) {
        val receiverInnerType = receiverType.type
        if (receiverInnerType is ShapeType) {
            return inferConstructorCallType(node, null, receiverInnerType, context)
        } else if (receiverInnerType is TypeFunction) {
            val typeFunctionInnerType = receiverInnerType.type
            if (typeFunctionInnerType is ShapeType) {
                return inferConstructorCallType(node, receiverInnerType, typeFunctionInnerType, context)
            }
        }
    } else if (receiverType is ListConstructorType) {
        return inferListCall(node, context)
    }

    return null
}

private fun inferFunctionCallType(
    node: CallNode,
    receiverType: FunctionType,
    context: TypeContext
): Type {
    val bindings = checkArguments(
        call = node,
        staticParameters = receiverType.staticParameters,
        positionalParameters = receiverType.positionalParameters,
        namedParameters = receiverType.namedParameters,
        context = context,
        allowMissing = false
    )

    val effect = replaceEffects(receiverType.effect, bindings)
    if (!isSubEffect(subEffect = effect, superEffect = context.effect)) {
        throw UnhandledEffectError(effect, source = node.source)
    }

    // TODO: handle unconstrained types
    return replaceStaticValuesInType(receiverType.returns, bindings)
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
        context = context,
        allowMissing = false
    )

    if (typeFunction == null) {
        return shapeType
    } else {
        return applyStatic(typeFunction, typeFunction.parameters.map({ parameter ->
            typeParameterBindings[parameter]!!
        }))
    }
}

internal fun inferPartialCallType(node: PartialCallNode, context: TypeContext): Type {
    val receiverType = inferType(node.receiver, context)

    if (receiverType is FunctionType) {
        val bindings = checkArguments(
            call = node,
            staticParameters = receiverType.staticParameters,
            positionalParameters = receiverType.positionalParameters,
            namedParameters = receiverType.namedParameters,
            context = context,
            allowMissing = true
        )
        // TODO: handle bindings
        return receiverType.copy(
            positionalParameters = receiverType.positionalParameters.drop(node.positionalArguments.size),
            namedParameters = receiverType.namedParameters.filterKeys { name ->
                !node.namedArguments.any { argument -> argument.name == name }
            }
        )
    } else {
        throw NotImplementedError()
    }
}

private fun checkArguments(
    call: CallBaseNode,
    staticParameters: List<StaticParameter>,
    positionalParameters: List<Type>,
    namedParameters: Map<String, Type>,
    context: TypeContext,
    allowMissing: Boolean
): StaticBindings {
    val positionalArguments = call.positionalArguments.zip(positionalParameters)
    if ((!allowMissing && call.positionalArguments.size < positionalParameters.size) || call.positionalArguments.size > positionalParameters.size) {
        throw WrongNumberOfArgumentsError(
            expected = positionalParameters.size,
            actual = call.positionalArguments.size,
            source = call.source
        )
    }

    for ((name, arguments) in call.namedArguments.groupBy(CallNamedArgumentNode::name)) {
        if (arguments.size > 1) {
            throw ArgumentAlreadyPassedError(name, source = arguments[1].source)
        }
    }

    val namedArguments = call.namedArguments.map({ argument ->
        val fieldType = namedParameters[argument.name]
        if (fieldType == null) {
            throw ExtraArgumentError(argument.name, source = argument.source)
        } else {
            argument.expression to fieldType
        }
    })

    if (!allowMissing) {
        val missingNamedArguments = namedParameters.keys - call.namedArguments.map({ argument -> argument.name })
        for (missingNamedArgument in missingNamedArguments) {
            throw MissingArgumentError(missingNamedArgument, source = call.source)
        }
    }

    val arguments = positionalArguments + namedArguments
    return checkArgumentTypes(
        staticParameters = staticParameters,
        staticArguments = call.staticArguments,
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
): StaticBindings {
    if (staticArguments.isEmpty()) {
        val typeParameters = staticParameters.filterIsInstance<TypeParameter>()
        val effectParameters = staticParameters.filterIsInstance<EffectParameter>()

        val inferredTypeArguments = typeParameters.map({ parameter -> TypeParameter(
            name = parameter.name,
            variance = parameter.variance
        ) })

        val constraints = TypeConstraintSolver(
            // TODO: need to regenerate effect parameters in the same way as type positionalParameters
            parameters = (inferredTypeArguments + effectParameters).toSet()
        )
        for (argument in arguments) {
            val parameterType = replaceStaticValuesInType(
                argument.second,
                typeParameters.zip(inferredTypeArguments).toMap()
            )
            val actualType = inferType(argument.first, context, hint = parameterType)
            if (!constraints.coerce(from = actualType, to = parameterType)) {
                throw UnexpectedTypeError(
                    expected = parameterType,
                    actual = actualType,
                    source = argument.first.source
                )
            }
        }

        val typeMap = typeParameters.zip(inferredTypeArguments)
            .associate({ (parameter, inferredArgument) ->
                val boundType = constraints.boundTypeFor(inferredArgument)
                if (boundType == null) {
                    throw CouldNotInferTypeParameterError(
                        parameter = parameter,
                        source = source
                    )
                } else {
                    parameter to boundType
                }
            })
        val effectMap = effectParameters.associate({ parameter ->
            parameter to constraints.boundEffectFor(parameter)
        })
        // TODO: handle unbound effects
        return typeMap + effectMap
    } else {
        if (staticArguments.size != staticParameters.size) {
            throw WrongNumberOfStaticArgumentsError(
                expected = staticParameters.size,
                actual = staticArguments.size,
                source = source
            )
        }

        val typeMap = mutableMapOf<TypeParameter, Type>()
        val effectMap = mutableMapOf<EffectParameter, Effect>()

        for ((staticParameter, staticArgument) in staticParameters.zip(staticArguments)) {
            staticParameter.accept(object: StaticParameter.Visitor<Unit> {
                override fun visit(parameter: TypeParameter) {
                    typeMap[parameter] = evalType(staticArgument, context)
                }

                override fun visit(parameter: EffectParameter) {
                    effectMap[parameter] = evalEffect(staticArgument, context)
                }
            })
        }

        val bindings = typeMap + effectMap

        checkArgumentTypes(
            staticParameters = listOf(),
            staticArguments = listOf(),
            arguments = arguments.map({ (expression, type) ->
                expression to replaceStaticValuesInType(type, bindings)
            }),
            source = source,
            context = context
        )

        return bindings
    }
}

private fun inferListCall(node: CallNode, context: TypeContext): Type {
    val typeParameter = TypeParameter("T", variance = Variance.COVARIANT)
    val constraints = TypeConstraintSolver(parameters = setOf(typeParameter))
    for (argument in node.positionalArguments) {
        val argumentType = inferType(argument, context)
        constraints.coerce(argumentType, typeParameter)
    }
    return applyStatic(ListType, listOf(constraints.boundTypeFor(typeParameter)!!))
}
