package org.shedlang.compiler.types

import org.shedlang.compiler.CannotUnionTypesError
import org.shedlang.compiler.all
import org.shedlang.compiler.zip3

fun isSubEffect(subEffect: Effect, superEffect: Effect): Boolean {
    val solver = TypeConstraintSolver(originalParameters = setOf())
    return solver.coerceEffect(from = subEffect, to = superEffect)
}

fun canCoerce(from: Type, to: Type): Boolean {
    return coerce(from = from, to = to) is CoercionResult.Success
}

fun canCoerce(from: Type, to: Type, freeParameters: Set<TypeLevelParameter>): Boolean {
    return coerce(from = from, to = to, parameters = freeParameters) is CoercionResult.Success
}

fun isEquivalentType(first: Type, second: Type): Boolean {
    return canCoerce(from = first, to = second) && canCoerce(from = second, to = first)
}

fun coerce(
    from: Type,
    to: Type,
    parameters: Set<TypeLevelParameter> = setOf()
): CoercionResult {
    return coerce(listOf(from to to), parameters = parameters)
}

fun coerce(
    constraints: List<Pair<Type, Type>>,
    parameters: Set<TypeLevelParameter> = setOf()
): CoercionResult {
    val solver = TypeConstraintSolver(originalParameters = parameters)
    for ((from, to) in constraints) {
        if (!solver.coerce(from = from, to = to)) {
            return CoercionResult.Failure
        }
    }
    return CoercionResult.Success(solver.bindings())
}

sealed class CoercionResult {
    class Success(val bindings: TypeLevelBindings): CoercionResult()
    object Failure: CoercionResult()
}

class TypeConstraintSolver(
    originalParameters: Set<TypeLevelParameter>
) {
    private sealed class TypeBound(val type: Type) {
        class Lower(type: Type): TypeBound(type = type)
        class Upper(type: Type): TypeBound(type = type)

        fun mapType(func: (Type) -> Type): TypeBound {
            return when (this) {
                is Lower -> Lower(func(type))
                is Upper -> Upper(func(type))
            }
        }
    }

    fun bindings(): TypeLevelBindings {
        return typeBindings.mapValues { (_, binding) -> binding.type } + effectBindings
    }

    private val typeBindings: MutableMap<TypeParameter, TypeBound> = mutableMapOf()
    private val effectBindings: MutableMap<EffectParameter, Effect> = mutableMapOf()
    // TODO: outside callers (such as inferPartialCallType indirectly) have no
    // way of checking for unbound parameters that aren't in originalParameters
    private val parameters: MutableSet<TypeLevelParameter> = originalParameters.toMutableSet()

    fun boundTypeFor(parameter: TypeParameter): Type? {
        val boundType = typeBindings[parameter]
        return if (boundType != null) {
            boundType.type
        } else if (parameter.variance == Variance.COVARIANT) {
            NothingType
        } else if (parameter.variance == Variance.CONTRAVARIANT) {
            AnyType
        } else {
            null
        }
    }

    fun explicitlyBoundTypeFor(parameter: TypeParameter): Type? {
        return typeBindings[parameter]?.type
    }

    fun boundEffectFor(parameter: EffectParameter): Effect {
        return effectBindings[parameter] ?: EmptyEffect
    }

    fun coerce(from: Type, to: Type): Boolean {
        if (from == to || to == AnyType || from == NothingType) {
            return true
        }

        if (to is TypeParameter && to in parameters && (to.shapeId == null || from.shapeId == to.shapeId)) {
            val boundType = typeBindings[to]
            when (boundType) {
                null -> {
                    bindType(to, TypeBound.Lower(from))
                    return true
                }
                is TypeBound.Upper -> {
                    return coerce(from = from, to = boundType.type)
                }
                is TypeBound.Lower -> {
                    try {
                        bindType(to, TypeBound.Lower(union(boundType.type, from)))
                    } catch (error: CannotUnionTypesError) {
                        return false
                    }
                    return true
                }
            }
        }

        if (from is TypeParameter && from in parameters && (from.shapeId == null || from.shapeId == to.shapeId)) {
            val boundType = typeBindings[from]
            if (boundType == null) {
                bindType(from, TypeBound.Upper(to))
                return true
            } else {
                return coerce(from = boundType.type, to = to)
            }
        }

        if (from is TypeAlias) {
            return coerce(from = from.aliasedType, to = to)
        }

        if (to is TypeAlias) {
            return coerce(from = from, to = to.aliasedType)
        }

        if (to is CastableType) {
            // TODO: Test replacement of static values
            val bindings = bindings()
            val discriminator = findDiscriminator(
                sourceType = replaceTypeLevelValuesInType(from, bindings),
                targetType = replaceTypeLevelValuesInType(to.type, bindings),
            )
            return discriminator != null
        }

        // TODO: deal with type parameters
        if (from is UnionType) {
            return from.members.all({ member -> coerce(from = member, to = to) })
        }

        if (to is UnionType) {
            // TODO: coerce mutates state, but we probably want to ignore changes
            // from failed attempts to coerce to a member
            return to.members.any({ member -> coerce(from = from, to = member) })
        }

        if (from is FunctionType && to is FunctionType) {
            if (from.typeLevelParameters.isEmpty()) {
                return (
                    from.positionalParameters.size == to.positionalParameters.size &&
                    from.positionalParameters.zip(to.positionalParameters, { fromArg, toArg -> coerce(from = toArg, to = fromArg) }).all() &&
                    from.namedParameters.keys == to.namedParameters.keys &&
                    from.namedParameters.all({ fromArg -> coerce(from = to.namedParameters[fromArg.key]!!, to = fromArg.value) }) &&
                    coerceEffect(from = from.effect, to = to.effect) &&
                    coerce(from = from.returns, to = to.returns)
                )
            } else {
                val typeLevelArguments = from.typeLevelParameters.map { parameter -> parameter.fresh() }
                parameters.addAll(typeLevelArguments)
                val unparameterizedFrom = replaceTypeLevelValuesInType(
                    from.copy(typeLevelParameters = listOf()),
                    from.typeLevelParameters.zip(typeLevelArguments).toMap()
                )
                return coerce(
                    from = unparameterizedFrom,
                    to = to
                )
            }
        }

        if (from is TupleType && to is TupleType) {
            return from.elementTypes.size == to.elementTypes.size &&
                from.elementTypes.zip(to.elementTypes).all { (fromElement, toElement) ->
                    coerce(from = fromElement, to = toElement)
                }
        }

        if (from is ShapeType && to is ShapeType) {
            val sameShapeId = from.shapeId == to.shapeId

            val canCoerceTypeLevelArguments = zip3(
                from.typeLevelParameters,
                from.typeLevelArguments,
                to.typeLevelArguments
            ) { parameter, fromArg, toArg ->
                parameter.accept(object : TypeLevelParameter.Visitor<Boolean> {
                    override fun visit(parameter: TypeParameter): Boolean {
                        return fromArg is Type && toArg is Type && when (parameter.variance) {
                            Variance.INVARIANT -> isEquivalentType(fromArg, toArg)
                            Variance.COVARIANT -> coerce(from = fromArg, to = toArg)
                            Variance.CONTRAVARIANT -> coerce(from = toArg, to = fromArg)
                        }
                    }

                    override fun visit(parameter: EffectParameter): Boolean {
                        return fromArg is Effect && toArg is Effect && coerceEffect(from = fromArg, to = toArg)
                    }
                })
            }.all()

            return sameShapeId && canCoerceTypeLevelArguments
        }

        if (from is TypeLevelValueType && from.value is Type && to is TypeLevelValueType && to.value is Type) {
            return isEquivalentType(from.value, to.value)
        }

        return false
    }

    private fun bindType(from: TypeParameter, to: TypeBound) {
        val existingTypeBindings = typeBindings.toMap()

        val newTo = to.mapType { toType -> replaceTypeLevelValuesInType(toType, bindings()) }
        typeBindings[from] = newTo
        for ((existingFrom, existingTo) in existingTypeBindings) {
            if (existingFrom != from) {
                // Test newTo instead of to here
                typeBindings[existingFrom] = existingTo.mapType { existingToType -> replaceTypeLevelValuesInType(existingToType, mapOf(from to newTo.type)) }
            }
        }
    }

    fun coerceEffect(from: Effect, to: Effect): Boolean {
        if (from == to) {
            return true
        }

        if (from == EmptyEffect) {
            return true
        }

        if (from is EffectUnion) {
            return from.members.all { member ->
                coerceEffect(from = member, to = to)
            }
        }

        if (to is EffectUnion) {
            return to.members.any { member ->
                coerceEffect(from = from, to = member)
            }
        }

        if (to is EffectParameter && to in parameters) {
            val boundEffect = effectBindings[to]
            if (boundEffect == null) {
                effectBindings[to] = from
                return true
            } else {
                return coerceEffect(from = from, to = boundEffect)
            }
        }

        if (from is EffectParameter && from in parameters) {
            val boundEffect = effectBindings[from]
            if (boundEffect == null) {
                effectBindings[from] = to
                return true
            } else {
                return coerceEffect(from = boundEffect, to = to)
            }
        }

        return false
    }

    private fun isEquivalentType(left: Type, right: Type): Boolean {
        return coerce(from = left, to = right) && coerce(from = right, to = left)
    }
}
