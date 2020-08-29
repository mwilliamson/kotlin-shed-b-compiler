package org.shedlang.compiler.typechecker

import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ModuleResult
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

internal fun inferCallType(node: CallNode, context: TypeContext): Type {
    fun infer(node: CallNode, context: TypeContext): Pair<Type, StaticBindings> {
        val receiver = node.receiver
        val receiverType = if (receiver is PartialCallNode && node.staticArguments.isEmpty() && receiver.staticArguments.isEmpty()) {
            val (type, bindings) = infer(
                CallNode(
                    receiver = receiver.receiver,
                    positionalArguments = receiver.positionalArguments + node.positionalArguments,
                    namedArguments = receiver.namedArguments + node.namedArguments,
                    staticArguments = listOf(),
                    hasEffect = node.hasEffect,
                    source = receiver.source,
                ),
                context.copy(),
            )
            val receiverType = inferPartialCallType(receiver, context, bindingsHint = bindings)
            context.addExpressionType(receiver, receiverType)
            receiverType
        } else {
            inferType(receiver, context)
        }
        val result = tryInferCallType(node, receiverType, context)
        if (result == null) {
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
                source = receiver.source
            )
        } else {
            return result
        }
    }

    val result = infer(node, context)
    return result.first
}

internal fun tryInferCallType(node: CallNode, receiverType: Type, context: TypeContext): Pair<Type, StaticBindings>? {
    if (receiverType is FunctionType) {
        return inferFunctionCallType(node, receiverType, context)
    }

    if (receiverType is StaticValueType) {
        val receiverInnerType = receiverType.value
        if (receiverInnerType is ShapeType) {
            return inferConstructorCallType(node, null, receiverInnerType, context)
        } else if (receiverInnerType is ParameterizedStaticValue) {
            val typeFunctionInnerType = receiverInnerType.value
            if (typeFunctionInnerType is ShapeType) {
                return inferConstructorCallType(node, receiverInnerType, typeFunctionInnerType, context)
            }
        }
    } else if (receiverType is CastType) {
        return Pair(inferCastCall(node, context), mapOf())
    } else if (receiverType is EmptyFunctionType) {
        return Pair(inferEmptyCall(node, context), mapOf())
    } else if (receiverType is VarargsType) {
        return Pair(inferVarargsCall(node, receiverType, context), mapOf())
    }

    return null
}

private fun inferFunctionCallType(
    node: CallNode,
    receiverType: FunctionType,
    context: TypeContext
): Pair<Type, StaticBindings> {
    val bindings = checkArguments(
        call = node,
        staticParameters = receiverType.staticParameters,
        positionalParameters = receiverType.positionalParameters,
        namedParameters = receiverType.namedParameters,
        context = context,
        allowMissing = false
    )

    val effect = replaceEffects(receiverType.effect, bindings)

    if (effect != EmptyEffect && !node.hasEffect) {
        throw UnhandledEffectError(effect, source = node.source)
    }

    if (effect == EmptyEffect && node.hasEffect) {
        throw ReceiverHasNoEffectsError(source = node.source)
    }

    if (!isSubEffect(subEffect = effect, superEffect = context.effect)) {
        throw UnhandledEffectError(effect, source = node.source)
    }

    // TODO: handle unconstrained types
    val returnType = replaceStaticValuesInType(receiverType.returns, bindings)
    return Pair(returnType, bindings)
}

private fun inferConstructorCallType(
    node: CallNode,
    typeFunction: ParameterizedStaticValue?,
    shapeType: ShapeType,
    context: TypeContext
): Pair<Type, StaticBindings> {
    if (node.positionalArguments.any()) {
        throw PositionalArgumentPassedToShapeConstructorError(source = node.positionalArguments.first().source)
    }

    val typeParameterBindings = checkArguments(
        call = node,
        staticParameters = typeFunction?.parameters ?: listOf(),
        positionalParameters = listOf(),
        namedParameters = shapeType.populatedFields
            .filter { field -> !field.value.isConstant }
            .mapValues { field -> field.value.type },
        context = context,
        allowMissing = false
    )

    if (typeFunction == null) {
        return Pair(shapeType, mapOf())
    } else {
        val type = applyStatic(typeFunction, typeFunction.parameters.map({ parameter ->
            typeParameterBindings[parameter]!!
        })) as Type
        return Pair(type, typeParameterBindings)
    }
}

internal fun inferPartialCallType(node: PartialCallNode, context: TypeContext, bindingsHint: StaticBindings? = null): Type {
    val receiverType = inferType(node.receiver, context)

    if (receiverType is FunctionType) {
        val bindings = checkArguments(
            call = node,
            staticParameters = receiverType.staticParameters,
            positionalParameters = receiverType.positionalParameters,
            namedParameters = receiverType.namedParameters,
            context = context,
            bindingsHint = bindingsHint,
            allowMissing = true
        )

        for (staticParameter in receiverType.staticParameters) {
            if (staticParameter !in bindings) {
                // TODO: handle this more appropriately
                throw Exception("unbound type parameter")
            }
        }

        val remainingPositionalParameters = receiverType.positionalParameters
            .drop(node.positionalArguments.size)
            .map { parameter -> replaceStaticValuesInType(parameter, bindings) }
        val remainingNamedParameters = receiverType.namedParameters
            .filterKeys { name -> !node.namedArguments.any { argument -> argument.name == name } }
            .mapValues { (name, parameter) -> replaceStaticValuesInType(parameter, bindings) }
        return FunctionType(
            staticParameters = listOf(),
            positionalParameters = remainingPositionalParameters,
            namedParameters = remainingNamedParameters,
            returns = replaceStaticValuesInType(receiverType.returns, bindings),
            effect = replaceEffects(receiverType.effect, bindings)
        )
    } else {
        throw NotImplementedError()
    }
}

private fun checkArguments(
    call: CallBaseNode,
    staticParameters: List<StaticParameter>,
    positionalParameters: List<Type>,
    namedParameters: Map<Identifier, Type>,
    context: TypeContext,
    bindingsHint: StaticBindings? = null,
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
        staticArgumentNodes = call.staticArguments,
        arguments = arguments,
        source = call.source,
        bindingsHint = bindingsHint,
        context = context
    )
}

private fun checkArgumentTypes(
    staticParameters: List<StaticParameter>,
    staticArgumentNodes: List<StaticExpressionNode>,
    arguments: List<Pair<ExpressionNode, Type>>,
    source: Source,
    bindingsHint: StaticBindings?,
    context: TypeContext
): StaticBindings {
    if (staticArgumentNodes.isEmpty() && bindingsHint == null) {
        val typeParameters = staticParameters.filterIsInstance<TypeParameter>()
        val effectParameters = staticParameters.filterIsInstance<EffectParameter>()

        val inferredTypeArguments = typeParameters.map(TypeParameter::fresh)
        val inferredEffectArguments = effectParameters.map(EffectParameter::fresh)

        val constraints = TypeConstraintSolver(
            // TODO: need to regenerate effect parameters in the same way as type positionalParameters
            parameters = (inferredTypeArguments + inferredEffectArguments).toSet()
        )

        fun generateBindings(): Map<StaticParameter, StaticValue> {
            val typeMap = typeParameters.zip(inferredTypeArguments)
                .associate { (parameter, inferredArgument) ->
                    val boundType = constraints.boundTypeFor(inferredArgument)
                    if (boundType == null) {
                        throw CouldNotInferTypeParameterError(
                            parameter = parameter,
                            source = source
                        )
                    } else {
                        parameter to boundType
                    }
                }
            val effectMap = effectParameters.zip(inferredEffectArguments)
                .associate { (parameter, inferredArgument) ->
                    parameter to constraints.boundEffectFor(inferredArgument)
                }
            // TODO: handle unbound effects
            return typeMap + effectMap
        }

        for (argument in arguments) {
            val parameterType = replaceStaticValuesInType(
                argument.second,
                (typeParameters.zip(inferredTypeArguments) + effectParameters.zip(inferredEffectArguments)).toMap()
            )
            val actualType = inferType(argument.first, context, hint = parameterType)
            if (!constraints.coerce(from = actualType, to = parameterType)) {
                throw UnexpectedTypeError(
                    expected = replaceStaticValuesInType(argument.second, generateBindings()),
                    actual = actualType,
                    source = argument.first.source
                )
            }
        }

        return generateBindings()
    } else {
        val bindings = if (bindingsHint == null) {
            if (staticArgumentNodes.size != staticParameters.size) {
                throw WrongNumberOfStaticArgumentsError(
                    expected = staticParameters.size,
                    actual = staticArgumentNodes.size,
                    source = source
                )
            }

            val typeMap = mutableMapOf<TypeParameter, Type>()
            val effectMap = mutableMapOf<EffectParameter, Effect>()

            for ((staticParameter, staticArgument) in staticParameters.zip(staticArgumentNodes)) {
                staticParameter.accept(object : StaticParameter.Visitor<Unit> {
                    override fun visit(parameter: TypeParameter) {
                        typeMap[parameter] = evalType(staticArgument, context)
                    }

                    override fun visit(parameter: EffectParameter) {
                        effectMap[parameter] = evalEffect(staticArgument, context)
                    }
                })
            }

            typeMap + effectMap
        } else {
            bindingsHint
        }

        checkArgumentTypes(
            staticParameters = listOf(),
            staticArgumentNodes = listOf(),
            arguments = arguments.map({ (expression, type) ->
                expression to replaceStaticValuesInType(type, bindings)
            }),
            source = source,
            bindingsHint = null,
            context = context
        )

        return bindings
    }
}

private fun inferCastCall(node: CallNode, context: TypeContext): Type {
    // TODO: check other arguments
    val fromType = metaTypeToType(inferType(node.positionalArguments[0], context))!!
    val toType = metaTypeToType(inferType(node.positionalArguments[1], context))!!
    // TODO: check discriminator exists

    val discriminator = findDiscriminator(
        sourceType = fromType,
        targetType = toType
    )!!
    context.addDiscriminator(node, discriminator)

    // TODO: failed module lookup
    val optionsModuleResult = context.module(ImportPath.absolute(listOf("Core", "Options")))
    when (optionsModuleResult) {
        is ModuleResult.Found -> {
            val someType = (optionsModuleResult.module.type.fieldType(Identifier("Option")) as StaticValueType).value as ParameterizedStaticValue
            return functionType(
                positionalParameters = listOf(fromType),
                returns = applyStatic(someType, listOf(toType)) as Type
            )
        }
        else -> throw NotImplementedError()
    }
}

private fun inferEmptyCall(node: CallNode, context: TypeContext): Type {
    // TODO: check number of static arguments
    // TODO: check type of static argument
    // TODO: check non-static arguments
    val staticArgument = evalStaticValue(node.staticArguments.single(), context) as ShapeType
    return createEmptyShapeType(staticArgument)
}

private fun inferVarargsCall(node: CallNode, type: VarargsType, context: TypeContext): Type {
    val typeParameters = type.cons.staticParameters.filterIsInstance<TypeParameter>()
    val inferredTypeArguments = typeParameters.map(TypeParameter::fresh)

    val headParameterType = type.cons.positionalParameters[0]
    val tailParameterType = type.cons.positionalParameters[1]

    return node.positionalArguments.foldRight(type.nil) { argument, currentType ->
        val constraints = TypeConstraintSolver(
            parameters = inferredTypeArguments.toSet()
        )
        fun partialTypeMap(): StaticBindings = typeParameters.zip(inferredTypeArguments).toMap()

        if (!constraints.coerce(from = currentType, to = replaceStaticValuesInType(tailParameterType, partialTypeMap()))) {
            throw CompilerError("failed to type-check varargs call", source = argument.source)
        }

        val argumentType = inferType(argument, context)
        if (!constraints.coerce(from = argumentType, to = replaceStaticValuesInType(headParameterType, partialTypeMap()))) {
            throw CompilerError("failed to type-check varargs call", source = argument.source)
        }

        fun typeMap() = typeParameters.zip(inferredTypeArguments)
            .associate { (parameter, inferredArgument) ->
                val boundType: StaticValue? = constraints.boundTypeFor(inferredArgument)
                if (boundType == null) {
                    throw CouldNotInferTypeParameterError(
                        parameter = parameter,
                        source = argument.source
                    )
                } else {
                    parameter as StaticParameter to boundType
                }
            }

        replaceStaticValuesInType(type.cons.returns, typeMap())
    }
}

internal fun inferStaticCallType(call: StaticCallNode, context: TypeContext): Type {
    val receiverType = inferType(call.receiver, context)
    val arguments = call.arguments.map { argument -> evalStaticValue(argument, context) }
    // TODO: handle error cases
    return StaticValueType(applyStatic((receiverType as StaticValueType).value as ParameterizedStaticValue, arguments))
}
