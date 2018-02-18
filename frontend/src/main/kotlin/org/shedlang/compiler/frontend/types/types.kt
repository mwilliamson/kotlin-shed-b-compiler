package org.shedlang.compiler.frontend.types

import org.shedlang.compiler.ast.UnknownSource
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.canCoerce
import org.shedlang.compiler.types.*

fun union(left: Type, right: Type): Type {
    if (canCoerce(from = right, to = left)) {
        return left
    } else if (canCoerce(from = left, to = right)) {
        return right
    } else {
        // TODO: should be list of shape types
        fun findMembers(type: Type): Pair<List<ShapeType>, TagField?> {
            return when (type) {
                is UnionType -> Pair(type.members, type.declaredTagField)
                is ShapeType -> Pair(listOf(type), type.tagValue?.tagField)
                else -> Pair(listOf(), null)
            }
        }

        val (leftMembers, leftTagField) = findMembers(left)
        val (rightMembers, rightTagField) = findMembers(right)

        if (leftTagField == null || rightTagField == null || leftTagField != rightTagField) {
            throw TypeCheckError("Cannot union types with different tag fields", source = UnknownSource)
        } else {
            return AnonymousUnionType(members = (leftMembers + rightMembers).distinct(), declaredTagField = leftTagField)
        }
    }
}

internal fun applyStatic(receiver: TypeFunction, arguments: List<StaticValue>): Type {
    val bindings = receiver.parameters.zip(arguments).toMap()
    return replaceStaticValuesInType(receiver.type, bindings = bindings)
}

internal typealias StaticBindings = Map<StaticParameter, StaticValue>

private fun replaceStaticValues(value: StaticValue, bindings: StaticBindings): StaticValue {
    return value.acceptStaticValueVisitor(object : StaticValue.Visitor<StaticValue> {
        override fun visit(effect: Effect): StaticValue {
            return replaceEffects(effect, bindings)
        }

        override fun visit(type: Type): StaticValue {
            return replaceStaticValuesInType(type, bindings)
        }
    })
}

internal fun replaceStaticValuesInType(type: Type, bindings: StaticBindings): Type {
    if (type is TypeParameter) {
        // TODO: handle non-type bindings
        return bindings.getOrElse(type, { type }) as Type
    } else if (type is UnionType) {
        return LazyUnionType(
            type.name,
            lazy({
                type.members.map({ memberType -> replaceStaticValuesInType(memberType, bindings) as ShapeType })
            }),
            staticArguments = type.staticArguments.map({ argument -> replaceStaticValues(argument, bindings) }),
            declaredTagField = type.declaredTagField
        )
    } else if (type is ShapeType) {
        return LazyShapeType(
            name = type.name,
            getFields = lazy({
                type.fields.mapValues({ field -> replaceStaticValuesInType(field.value, bindings) })
            }),
            shapeId = type.shapeId,
            staticParameters = type.staticParameters,
            staticArguments = type.staticArguments.map({ argument -> replaceStaticValues(argument, bindings) }),
            declaredTagField = type.declaredTagField,
            getTagValue = lazy { type.tagValue }
        )
    } else if (type is FunctionType) {
        return FunctionType(
            positionalParameters = type.positionalParameters.map({ parameter -> replaceStaticValuesInType(parameter, bindings) }),
            namedParameters = type.namedParameters.mapValues({ parameter -> replaceStaticValuesInType(parameter.value, bindings) }),
            effect = replaceEffects(type.effect, bindings),
            returns = replaceStaticValuesInType(type.returns, bindings),
            staticParameters = type.staticParameters
        )
    } else if (type is UnitType || type is BoolType || type is IntType || type is StringType || type is AnyType) {
        return type
    } else {
        throw NotImplementedError("Type replacement not implemented for: " + type)
    }
}

internal fun replaceEffects(effect: Effect, bindings: Map<StaticParameter, StaticValue>): Effect {
    val effectParameter = effect as? EffectParameter
    if (effectParameter == null) {
        return effect
    } else {
        // TODO: handle non-effect bindings
        return bindings.getOrElse(effect, { effect }) as Effect
    }
}
