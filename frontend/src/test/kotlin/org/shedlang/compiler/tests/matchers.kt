package org.shedlang.compiler.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.testing.allOf
import org.shedlang.compiler.testing.isMap
import org.shedlang.compiler.testing.isSequence
import org.shedlang.compiler.types.*


internal fun isFunctionType(
    arguments: Matcher<List<Type>> = anything,
    returnType: Matcher<Type> = anything,
    namedArguments: Matcher<Map<String, Type>> = anything,
    effects: Matcher<Iterable<Effect>> = anything
): Matcher<Type> = cast(allOf(
    has(FunctionType::positionalArguments, arguments),
    has(FunctionType::namedArguments, namedArguments),
    has(FunctionType::returns, returnType),
    has(FunctionType::effects, effects)
))

internal fun isShapeType(
    name: Matcher<String> = anything,
    typeArguments: Matcher<List<Type>> = anything,
    shapeId: Matcher<Int> = anything
): Matcher<Type> = cast(allOf(
    has(ShapeType::name, name),
    has(ShapeType::typeArguments, typeArguments),
    has(ShapeType::shapeId, shapeId)
))

internal fun isShapeType(
    name: Matcher<String> = anything,
    typeArguments: Matcher<List<Type>> = anything,
    fields: List<Pair<String, Matcher<Type>>>
): Matcher<Type> = cast(allOf(
    has(ShapeType::name, name),
    has(ShapeType::typeArguments, typeArguments),
    has(ShapeType::fields, isMap(*fields.toTypedArray()))
))

internal fun isUnionType(
    name: Matcher<String> = anything,
    typeArguments: Matcher<List<Type>> = anything,
    members: Matcher<List<Type>> = anything
): Matcher<Type> = cast(allOf(
    has(UnionType::name, name),
    has(UnionType::typeArguments, typeArguments),
    has(UnionType::members, members)
))

internal val isAnyType: Matcher<Type> = cast(equalTo(AnyType))
internal val isNothingType: Matcher<Type> = cast(equalTo(NothingType))
internal val isUnitType: Matcher<Type> = cast(equalTo(UnitType))
internal val isIntType: Matcher<Type> = cast(equalTo(IntType))
internal val isBoolType: Matcher<Type> = cast(equalTo(BoolType))
internal val isStringType: Matcher<Type> = cast(equalTo(StringType))

internal fun isMetaType(type: Matcher<Type>): Matcher<Type> = cast(has(MetaType::type, type))
internal fun isEffectType(effect: Matcher<Effect>): Matcher<Type> = cast(has(EffectType::effect, effect))

internal fun isListType(elementType: Matcher<Type>): Matcher<Type> = isShapeType(
    shapeId = equalTo(listTypeShapeId),
    typeArguments = isSequence(elementType)
)

internal fun isTypeFunction(
    parameters: Matcher<List<TypeParameter>>,
    type: Matcher<Type> = anything
): Matcher<Type> = cast(allOf(
    has(TypeFunction::parameters, parameters),
    has(TypeFunction::type, type)
))

internal fun isTypeParameter(
    name: Matcher<String>,
    variance: Matcher<Variance>
): Matcher<Type> = cast(allOf(
    has(TypeParameter::name, name),
    has(TypeParameter::variance, variance)
))

internal val isInvariant = equalTo(Variance.INVARIANT)
internal val isCovariant = equalTo(Variance.COVARIANT)
internal val isContravariant = equalTo(Variance.CONTRAVARIANT)

internal fun isEquivalentType(type: Type): Matcher<Type> {
    return object: Matcher.Primitive<Type>() {
        override fun invoke(actual: Type): MatchResult {
            if (org.shedlang.compiler.typechecker.isEquivalentType(type, actual)) {
                return MatchResult.Match
            } else {
                return MatchResult.Mismatch("was: " + actual)
            }
        }

        override val description: String
            get() = "is equivalent to " + type

    }
}
