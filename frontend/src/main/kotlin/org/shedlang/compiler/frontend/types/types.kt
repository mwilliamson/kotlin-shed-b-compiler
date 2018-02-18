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

internal fun applyType(receiver: TypeFunction, arguments: List<Type>): Type {
    val typeMap = receiver.parameters.zip(arguments).toMap()
    return replaceTypes(receiver.type, StaticBindings(types = typeMap, effects = mapOf()))
}

internal class StaticBindings(
    val types: Map<TypeParameter, Type>,
    val effects: Map<EffectParameter, Effect> = mapOf()
)

internal fun replaceTypes(type: Type, bindings: StaticBindings): Type {
    if (type is TypeParameter) {
        return bindings.types.getOrElse(type, { type })
    } else if (type is UnionType) {
        return LazyUnionType(
            type.name,
            lazy({
                type.members.map({ memberType -> replaceTypes(memberType, bindings) as ShapeType })
            }),
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, bindings) }),
            declaredTagField = type.declaredTagField
        )
    } else if (type is ShapeType) {
        return LazyShapeType(
            name = type.name,
            getFields = lazy({
                type.fields.mapValues({ field -> replaceTypes(field.value, bindings) })
            }),
            shapeId = type.shapeId,
            typeParameters = type.typeParameters,
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, bindings) }),
            declaredTagField = type.declaredTagField,
            getTagValue = lazy { type.tagValue }
        )
    } else if (type is FunctionType) {
        return FunctionType(
            positionalParameters = type.positionalParameters.map({ parameter -> replaceTypes(parameter, bindings) }),
            namedParameters = type.namedParameters.mapValues({ parameter -> replaceTypes(parameter.value, bindings) }),
            effect = replaceEffects(type.effect, bindings),
            returns = replaceTypes(type.returns, bindings),
            staticParameters = type.staticParameters
        )
    } else if (type is UnitType || type is BoolType || type is IntType || type is StringType || type is AnyType) {
        return type
    } else {
        throw NotImplementedError("Type replacement not implemented for: " + type)
    }
}

internal fun replaceEffects(effect: Effect, bindings: StaticBindings): Effect {
    return bindings.effects[effect] ?: effect
}
