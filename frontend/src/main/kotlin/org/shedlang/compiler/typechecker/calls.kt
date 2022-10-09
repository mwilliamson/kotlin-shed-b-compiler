package org.shedlang.compiler.typechecker

import org.shedlang.compiler.InternalCompilerError
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

internal fun inferCallType(node: CallNode, context: TypeContext): Type {
    fun infer(node: CallNode, context: TypeContext): Pair<Type, TypeLevelBindings> {
        val receiver = node.receiver
        val receiverType = if (receiver is PartialCallNode && node.typeLevelArguments.isEmpty() && receiver.typeLevelArguments.isEmpty()) {
            val (type, bindings) = infer(
                CallNode(
                    receiver = receiver.receiver,
                    positionalArguments = receiver.positionalArguments + node.positionalArguments,
                    fieldArguments = receiver.fieldArguments + node.fieldArguments,
                    typeLevelArguments = listOf(),
                    hasEffect = node.hasEffect,
                    source = receiver.source,
                    operatorSource = receiver.operatorSource,
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
                    typeLevelParameters = listOf(),
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

internal fun tryInferCallType(node: CallNode, receiverType: Type, context: TypeContext): Pair<Type, TypeLevelBindings>? {
    val analysis = analyseCallReceiver(receiverType)

    return when (analysis) {
        is CallReceiverAnalysis.Function -> inferNormalCallType(
            node,
            analysis.receiverType,
            splatType = null,
            context = context,
        )

        is CallReceiverAnalysis.Constructor -> inferNormalCallType(
            node,
            analysis.receiverType(),
            splatType = analysis.shapeType,
            context = context,
        )

        is CallReceiverAnalysis.Varargs -> Pair(
            inferVarargsCall(node, analysis.receiverType, context),
            mapOf(),
        )

        is CallReceiverAnalysis.NotCallable -> null
    }
}
private sealed class CallReceiverAnalysis {
    class Function(val receiverType: FunctionType): CallReceiverAnalysis()
    class Constructor(
        val typeFunction: TypeConstructor?,
        val shapeType: Type,
    ): CallReceiverAnalysis() {
        fun receiverType(): FunctionType {
            val returnType = if (typeFunction == null) {
                shapeType
            } else {
                applyTypeLevel(typeFunction, typeFunction.parameters)
            }

            return functionType(
                typeLevelParameters = typeFunction?.parameters ?: listOf(),
                positionalParameters = listOf(),
                namedParameters = shapeType.fields!!
                    .mapValues { field -> field.value.type },
                returns = returnType
            )
        }
    }
    class Varargs(val receiverType: VarargsType): CallReceiverAnalysis()
    object NotCallable: CallReceiverAnalysis()
}

private fun analyseCallReceiver(receiverType: Type): CallReceiverAnalysis {
    if (receiverType is FunctionType) {
        return CallReceiverAnalysis.Function(receiverType = receiverType)
    } else if (receiverType is TypeLevelValueType) {
        val receiverInnerType = receiverType.value
        if (receiverInnerType is ShapeType) {
            return CallReceiverAnalysis.Constructor(typeFunction = null, shapeType = receiverInnerType)
        } else if (receiverInnerType is TypeConstructor) {
            val typeFunctionInnerType = receiverInnerType.genericType
            if (typeFunctionInnerType is ShapeType) {
                return CallReceiverAnalysis.Constructor(
                    typeFunction = receiverInnerType,
                    shapeType = applyTypeLevel(receiverInnerType, receiverInnerType.parameters)
                )
            }
        }
    } else if (receiverType is VarargsType) {
        return CallReceiverAnalysis.Varargs(receiverType)
    }

    return CallReceiverAnalysis.NotCallable
}

private fun inferNormalCallType(
    node: CallNode,
    receiverType: FunctionType,
    // TODO: make shape type
    splatType: Type?,
    context: TypeContext,
): Pair<Type, TypeLevelBindings> {
    val bindings = checkArguments(
        call = node,
        typeLevelParameters = receiverType.typeLevelParameters,
        positionalParameters = receiverType.positionalParameters,
        namedParameters = receiverType.namedParameters,
        splatType = splatType,
        context = context,
        allowMissing = false
    )

    val effect = replaceEffects(receiverType.effect, bindings)

    if (effect != EmptyEffect && !node.hasEffect) {
        throw CallWithEffectMissingEffectFlag(source = node.operatorSource)
    }

    if (effect == EmptyEffect && node.hasEffect) {
        throw ReceiverHasNoEffectsError(source = node.operatorSource)
    }

    if (!isSubEffect(subEffect = effect, superEffect = context.effect)) {
        throw UnhandledEffectError(effect, source = node.operatorSource)
    }

    // TODO: handle unconstrained types
    val returnType = replaceTypeLevelValuesInType(receiverType.returns, bindings)
    return Pair(returnType, bindings)
}

internal fun inferPartialCallType(node: PartialCallNode, context: TypeContext, bindingsHint: TypeLevelBindings? = null): Type {
    val receiverType = inferType(node.receiver, context)

    val analysis = analyseCallReceiver(receiverType)

    return when (analysis) {
        is CallReceiverAnalysis.Function -> inferPartialNormalCallType(
            node = node,
            receiverType = analysis.receiverType,
            context = context,
            bindingsHint = bindingsHint,
        )

        is CallReceiverAnalysis.Constructor -> inferPartialNormalCallType(
            node = node,
            receiverType = analysis.receiverType(),
            context = context,
            bindingsHint = bindingsHint,
        )

        else -> throw NotImplementedError()
    }
}

private fun inferPartialNormalCallType(
    node: PartialCallNode,
    receiverType: FunctionType,
    context: TypeContext,
    bindingsHint: TypeLevelBindings? = null,
): Type {
    val bindings = checkArguments(
        call = node,
        typeLevelParameters = receiverType.typeLevelParameters,
        positionalParameters = receiverType.positionalParameters,
        namedParameters = receiverType.namedParameters,
        context = context,
        bindingsHint = bindingsHint,
        allowMissing = true
    )

    for (typeLevelParameter in receiverType.typeLevelParameters) {
        if (typeLevelParameter !in bindings) {
            // TODO: handle this more appropriately
            throw Exception("unbound type parameter")
        }
    }

    val remainingPositionalParameters = receiverType.positionalParameters
        .drop(node.positionalArguments.size)
        .map { parameter -> replaceTypeLevelValuesInType(parameter, bindings) }
    val remainingNamedParameters = receiverType.namedParameters
        // TODO: Handle splats
        .filterKeys { name -> !node.fieldArguments.any { argument -> (argument as FieldArgumentNode.Named).name == name } }
        .mapValues { (name, parameter) -> replaceTypeLevelValuesInType(parameter, bindings) }
    return FunctionType(
        typeLevelParameters = listOf(),
        positionalParameters = remainingPositionalParameters,
        namedParameters = remainingNamedParameters,
        returns = replaceTypeLevelValuesInType(receiverType.returns, bindings),
        effect = replaceEffects(receiverType.effect, bindings)
    )
}

private fun checkArguments(
    call: CallBaseNode,
    typeLevelParameters: List<TypeLevelParameter>,
    positionalParameters: List<Type>,
    namedParameters: Map<Identifier, Type>,
    context: TypeContext,
    bindingsHint: TypeLevelBindings? = null,
    // TODO: make shape type
    splatType: Type? = null,
    allowMissing: Boolean
): TypeLevelBindings {
    val positionalArgumentsWithTypes = call.positionalArguments.zip(positionalParameters)
    if ((!allowMissing && call.positionalArguments.size < positionalParameters.size) || call.positionalArguments.size > positionalParameters.size) {
        throw WrongNumberOfArgumentsError(
            expected = positionalParameters.size,
            actual = call.positionalArguments.size,
            source = call.operatorSource
        )
    }

    val explicitNamedArgumentsGroupedByName = call.fieldArguments
        .filterIsInstance<FieldArgumentNode.Named>()
        .groupBy(FieldArgumentNode.Named::name)

    for ((name, arguments) in explicitNamedArgumentsGroupedByName) {
        if (arguments.size > 1) {
            throw ArgumentAlreadyPassedError(name, source = arguments[1].source)
        }
    }

    val namedArgumentNames = mutableSetOf<Identifier>()
    val namedArgumentsWithTypes = mutableListOf<Pair<ExpressionNode, Type>>()

    for (argument in call.fieldArguments) {
        when (argument) {
            is FieldArgumentNode.Named -> {
                val fieldType = namedParameters[argument.name]
                if (fieldType == null) {
                    throw ExtraArgumentError(argument.name, source = argument.source)
                } else {
                    namedArgumentNames += argument.name
                    namedArgumentsWithTypes.add(Pair(argument.expression, fieldType))
                }
            }
            is FieldArgumentNode.Splat -> {
                if (splatType == null) {
                    throw UnexpectedSplatArgumentError(source = argument.source)
                } else {
                    // TODO: handle non-shape types
                    // TODO: handle generic types
                    val type = inferType(argument.expression, context = context)
                    namedArgumentNames += type.fields!!.keys
                    namedArgumentsWithTypes.add(Pair(argument.expression, splatType))
                    type.fields!!.mapValues { (_, field) -> field.type }
                }
            }
        }
        // TODO: check for redundant arguments
    }

    if (!allowMissing) {
        val missingNamedArguments = namedParameters.keys - namedArgumentNames
        for (missingNamedArgument in missingNamedArguments) {
            throw MissingArgumentError(missingNamedArgument, source = call.operatorSource)
        }
    }

    val arguments = positionalArgumentsWithTypes + namedArgumentsWithTypes
    return checkArgumentTypes(
        typeLevelParameters = typeLevelParameters,
        typeLevelArgumentNodes = call.typeLevelArguments,
        arguments = arguments,
        source = call.operatorSource,
        bindingsHint = bindingsHint,
        context = context
    )
}

private fun checkArgumentTypes(
    typeLevelParameters: List<TypeLevelParameter>,
    typeLevelArgumentNodes: List<TypeLevelExpressionNode>,
    arguments: List<Pair<ExpressionNode, Type>>,
    source: Source,
    bindingsHint: TypeLevelBindings?,
    context: TypeContext
): TypeLevelBindings {
    if (typeLevelArgumentNodes.isEmpty() && bindingsHint == null) {
        val typeParameters = typeLevelParameters.filterIsInstance<TypeParameter>()
        val effectParameters = typeLevelParameters.filterIsInstance<EffectParameter>()

        val inferredTypeArguments = typeParameters.map(TypeParameter::fresh)
        val inferredEffectArguments = effectParameters.map(EffectParameter::fresh)

        val constraints = TypeConstraintSolver(
            // TODO: need to regenerate effect parameters in the same way as type positionalParameters
            originalParameters = (inferredTypeArguments + inferredEffectArguments).toSet()
        )

        fun generateKnownBindings(): Map<TypeLevelParameter, TypeLevelValue> {
            val typeMap = typeParameters.zip(inferredTypeArguments)
                .associate { (parameter, inferredArgument) ->
                    val boundType = constraints.explicitlyBoundTypeFor(inferredArgument)
                    if (boundType != null) {
                        parameter to boundType
                    } else {
                        parameter to inferredArgument
                    }
                }
            // TODO: handle effects
            val effectMap = effectParameters.zip(inferredEffectArguments)
            // TODO: handle unbound effects
            return typeMap + effectMap
        }

        fun generateCompleteBindings(): Map<TypeLevelParameter, TypeLevelValue> {
            val typeMap = typeParameters.zip(inferredTypeArguments)
                .associate { (parameter, inferredArgument) ->
                    val boundType = constraints.boundTypeFor(inferredArgument)
                    if (boundType != null) {
                        parameter to boundType
                    } else {
                        throw CouldNotInferTypeParameterError(
                            parameter = parameter,
                            source = source
                        )
                    }
                }
            val effectMap = effectParameters.zip(inferredEffectArguments)
                .associate { (parameter, inferredArgument) ->
                    parameter to constraints.boundEffectFor(inferredArgument)
                }
            // TODO: handle unbound effects
            return typeMap + effectMap
        }

        for ((argument, unboundParameterType) in arguments.sortedBy { (argument, _) -> argument is FunctionNode && argument.parameters.any { parameter -> parameter.type == null } }) {
            val knownBindings = generateKnownBindings()
            val parameterHintType = replaceTypeLevelValuesInType(
                unboundParameterType,
                knownBindings
            )
            val actualType = inferType(argument, context, hint = parameterHintType)

            val parameterType = replaceTypeLevelValuesInType(
                unboundParameterType,
                (typeParameters.zip(inferredTypeArguments) + effectParameters.zip(inferredEffectArguments)).toMap()
            )
            if (!constraints.coerce(from = actualType, to = parameterType)) {
                throw UnexpectedTypeError(
                    expected = parameterHintType,
                    actual = actualType,
                    source = argument.source
                )
            }
        }

        return generateCompleteBindings()
    } else {
        val bindings = if (bindingsHint == null) {
            if (typeLevelArgumentNodes.size != typeLevelParameters.size) {
                throw WrongNumberOfTypeLevelArgumentsError(
                    expected = typeLevelParameters.size,
                    actual = typeLevelArgumentNodes.size,
                    source = source
                )
            }

            val typeMap = mutableMapOf<TypeParameter, Type>()
            val effectMap = mutableMapOf<EffectParameter, Effect>()

            for ((typeLevelParameter, typeLevelArgument) in typeLevelParameters.zip(typeLevelArgumentNodes)) {
                typeLevelParameter.accept(object : TypeLevelParameter.Visitor<Unit> {
                    override fun visit(parameter: TypeParameter) {
                        typeMap[parameter] = evalType(typeLevelArgument, context)
                    }

                    override fun visit(parameter: EffectParameter) {
                        effectMap[parameter] = evalEffect(typeLevelArgument, context)
                    }
                })
            }

            typeMap + effectMap
        } else {
            bindingsHint
        }

        checkArgumentTypes(
            typeLevelParameters = listOf(),
            typeLevelArgumentNodes = listOf(),
            arguments = arguments.map({ (expression, type) ->
                expression to replaceTypeLevelValuesInType(type, bindings)
            }),
            source = source,
            bindingsHint = null,
            context = context
        )

        return bindings
    }
}

private fun inferVarargsCall(node: CallNode, type: VarargsType, context: TypeContext): Type {
    verifyNoSplatArguments(node)

    val typeParameters = type.cons.typeLevelParameters.filterIsInstance<TypeParameter>()
    val inferredTypeArguments = typeParameters.map(TypeParameter::fresh)

    val headParameterType = type.cons.positionalParameters[0]
    val tailParameterType = type.cons.positionalParameters[1]

    return node.positionalArguments.foldRight(type.nil) { argument, currentType ->
        val constraints = TypeConstraintSolver(
            originalParameters = inferredTypeArguments.toSet()
        )
        fun partialTypeMap(): TypeLevelBindings = typeParameters.zip(inferredTypeArguments).toMap()

        if (!constraints.coerce(from = currentType, to = replaceTypeLevelValuesInType(tailParameterType, partialTypeMap()))) {
            throw InternalCompilerError("failed to type-check varargs call", source = argument.source)
        }

        val argumentType = inferType(argument, context)
        if (!constraints.coerce(from = argumentType, to = replaceTypeLevelValuesInType(headParameterType, partialTypeMap()))) {
            throw InternalCompilerError("failed to type-check varargs call", source = argument.source)
        }

        fun typeMap() = typeParameters.zip(inferredTypeArguments)
            .associate { (parameter, inferredArgument) ->
                val boundType: TypeLevelValue? = constraints.boundTypeFor(inferredArgument)
                if (boundType == null) {
                    throw CouldNotInferTypeParameterError(
                        parameter = parameter,
                        source = argument.source
                    )
                } else {
                    parameter as TypeLevelParameter to boundType
                }
            }

        replaceTypeLevelValuesInType(type.cons.returns, typeMap())
    }
}

private fun verifyNoSplatArguments(call: CallBaseNode) {
    val splatArgument = call.fieldArguments.filterIsInstance<FieldArgumentNode.Splat>().firstOrNull()
    if (splatArgument != null) {
        throw UnexpectedSplatArgumentError(source = splatArgument.source)
    }
}

internal fun inferTypeLevelCallType(call: TypeLevelCallNode, context: TypeContext): Type {
    val receiverType = inferType(call.receiver, context)
    val arguments = call.arguments.map { argument -> evalTypeLevelValue(argument, context) }
    // TODO: handle error cases
    return TypeLevelValueType(applyTypeLevel((receiverType as TypeLevelValueType).value as TypeConstructor, arguments))
}
