package org.shedlang.compiler.typechecker

import org.shedlang.compiler.all
import org.shedlang.compiler.types.*
import org.shedlang.compiler.zip3

internal fun canCoerce(from: Type, to: Type): Boolean {
    return coerce(from = from, to = to) is CoercionResult.Success
}

internal fun isEquivalentType(first: Type, second: Type): Boolean {
    return canCoerce(from = first, to = second) && canCoerce(from = second, to = first)
}

internal fun coerce(
    from: Type,
    to: Type,
    parameters: Set<TypeParameter> = setOf()
): CoercionResult {
    return coerce(listOf(from to to), parameters = parameters)
}

internal fun coerce(
    constraints: List<Pair<Type, Type>>,
    parameters: Set<TypeParameter> = setOf()
): CoercionResult {
    val solver = TypeConstraintSolver(parameters = parameters)
    for ((from, to) in constraints) {
        if (!solver.coerce(from = from, to = to)) {
            return CoercionResult.Failure
        }
    }
    return CoercionResult.Success(solver.typeBindings)
}

internal sealed class CoercionResult {
    internal class Success(val bindings: Map<TypeParameter, Type>): CoercionResult()
    internal object Failure: CoercionResult()
}

internal class TypeConstraintSolver(
    private val parameters: Set<StaticParameter>,
    internal val typeBindings: MutableMap<TypeParameter, Type> = mutableMapOf(),
    internal val effectBindings: MutableMap<EffectParameter, Effect> = mutableMapOf(),
    private val closed: MutableSet<TypeParameter> = mutableSetOf()
) {
    fun coerce(from: Type, to: Type): Boolean {
        if (from == to || to == AnyType || from == NothingType) {
            return true
        }

        // TODO: deal with type parameters
        if (from is UnionType) {
            return from.members.all({ member -> coerce(from = member, to = to) })
        }

        if (to is UnionType) {
            return to.members.any({ member -> coerce(from = from, to = member) })
        }

        if (from is FunctionType && to is FunctionType) {
            return (
                from.staticParameters.isEmpty() && to.staticParameters.isEmpty() &&
                    from.positionalArguments.size == to.positionalArguments.size &&
                    from.positionalArguments.zip(to.positionalArguments, { fromArg, toArg -> coerce(from = toArg, to = fromArg) }).all() &&
                    from.namedArguments.keys == to.namedArguments.keys &&
                    from.namedArguments.all({ fromArg -> coerce(from = to.namedArguments[fromArg.key]!!, to = fromArg.value) }) &&
                    from.effects.size == to.effects.size &&
                    from.effects.zip(to.effects, { fromEffect, toEffect -> coerceEffect(from = fromEffect, to = toEffect)}).all() &&
                    coerce(from = from.returns, to = to.returns)
                )
        }

        if (from is ShapeType && to is ShapeType) {
            return from.shapeId == to.shapeId && zip3(
                from.typeParameters,
                from.typeArguments,
                to.typeArguments,
                { parameter, fromArg, toArg -> when (parameter.variance) {
                    Variance.INVARIANT -> isEquivalentType(fromArg, toArg)
                    Variance.COVARIANT -> coerce(from = fromArg, to = toArg)
                    Variance.CONTRAVARIANT -> coerce(from = toArg, to = fromArg)
                }}
            ).all()
        }

        if (to is TypeParameter && to in parameters) {
            val boundType = typeBindings[to]
            if (boundType == null) {
                typeBindings[to] = from
                return true
            } else if (to in closed) {
                return coerce(from = from, to = boundType)
            } else {
                typeBindings[to] = union(boundType, from)
                return true
            }
        }

        if (from is TypeParameter && from in parameters) {
            val boundType = typeBindings[from]
            if (boundType == null) {
                typeBindings[from] = to
                closed.add(from)
                return true
            } else {
                return coerce(from = boundType, to = to)
            }
        }

        return false
    }

    fun coerceEffect(from: Effect, to: Effect): Boolean {
        if (from == to) {
            return true
        }

        if (to is EffectParameter && to in parameters) {
            val boundEffect = effectBindings[to]
            if (boundEffect == null) {
                effectBindings[to] = from
                return true
            }
        }

        return false
    }

    private fun isEquivalentType(left: Type, right: Type): Boolean {
        return coerce(from = left, to = right) && coerce(from = right, to = left)
    }
}
