package org.shedlang.compiler.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.*


fun isType(type: Type): Matcher<Type> = equalTo(type)

fun isFunctionType(
    staticParameters: Matcher<List<StaticParameter>> = anything,
    positionalParameters: Matcher<List<Type>> = anything,
    returnType: Matcher<Type> = anything,
    namedParameters: Matcher<Map<Identifier, Type>> = anything,
    effect: Matcher<Effect> = anything
): Matcher<Type> = cast(allOf(
    has(FunctionType::staticParameters, staticParameters),
    has(FunctionType::positionalParameters, positionalParameters),
    has(FunctionType::namedParameters, namedParameters),
    has(FunctionType::returns, returnType),
    has(FunctionType::effect, effect)
))

fun isTupleType(
    elementTypes: Matcher<List<Type>> = anything
): Matcher<Type> = cast(
    has(TupleType::elementTypes, elementTypes)
)

fun isTypeAlias(
    name: Matcher<Identifier>,
    aliasedType: Matcher<Type>
): Matcher<StaticValue> = cast(allOf(
    has(TypeAlias::name, name),
    has(TypeAlias::aliasedType, aliasedType)
))

fun isShapeType(
    shapeId: Matcher<Int> = anything,
    name: Matcher<Identifier> = anything,
    staticParameters: Matcher<List<StaticParameter>> = anything,
    staticArguments: Matcher<List<StaticValue>> = anything,
    fields: Matcher<Collection<Field>> = anything
): Matcher<StaticValue> = cast(allOf(
    has(ShapeType::shapeId, shapeId),
    has(ShapeType::name, name),
    has(ShapeType::staticParameters, staticParameters),
    has(ShapeType::staticArguments, staticArguments),
    has("fields", { type -> type.fields.values }, fields)
))

fun isField(
    name: Matcher<Identifier> = anything,
    type: Matcher<Type> = anything,
    isConstant: Matcher<Boolean> = anything,
    shapeId: Matcher<Int> = anything
) = allOf(
    has(Field::name, name),
    has(Field::type, type),
    has(Field::isConstant, isConstant),
    has(Field::shapeId, shapeId)
)

fun isUnionType(
    name: Matcher<Identifier> = anything,
    staticArguments: Matcher<List<StaticValue>> = anything,
    members: Matcher<List<Type>> = anything
): Matcher<StaticValue> = cast(allOf(
    has(UnionType::name, name),
    has(UnionType::staticArguments, staticArguments),
    has(UnionType::members, members)
))

val isAnyType: Matcher<StaticValue> = cast(equalTo(AnyType))
val isNothingType: Matcher<StaticValue> = cast(equalTo(NothingType))
val isUnitType: Matcher<StaticValue> = cast(equalTo(UnitType))
val isIntType: Matcher<StaticValue> = cast(equalTo(IntType))
val isBoolType: Matcher<StaticValue> = cast(equalTo(BoolType))
val isStringType: Matcher<StaticValue> = cast(equalTo(StringType))

fun isMetaType(type: Matcher<Type>): Matcher<StaticValue> = cast(has(MetaType::type, type))

fun isEffectType(effect: Matcher<Effect>): Matcher<StaticValue> = cast(has(EffectType::effect, effect))

fun isTypeFunction(
    parameters: Matcher<List<StaticParameter>>,
    type: Matcher<Type> = anything
): Matcher<Type> = cast(allOf(
    has(TypeFunction::parameters, parameters),
    has(TypeFunction::type, type)
))

fun isTypeParameter(
    name: Matcher<Identifier> = anything,
    variance: Matcher<Variance> = anything
): Matcher<StaticValue> = cast(allOf(
    has(TypeParameter::name, name),
    has(TypeParameter::variance, variance)
))

val isInvariant = equalTo(Variance.INVARIANT)
val isCovariant = equalTo(Variance.COVARIANT)
val isContravariant = equalTo(Variance.CONTRAVARIANT)

fun isEquivalentType(type: Type): Matcher<Type> {
    return object: Matcher.Primitive<Type>() {
        override fun invoke(actual: Type): MatchResult {
            if (isEquivalentType(type, actual)) {
                return MatchResult.Match
            } else {
                return MatchResult.Mismatch("was: " + actual)
            }
        }

        override val description: String
            get() = "is equivalent to " + type

    }
}

val isUnionTypeGroup: Matcher<TypeGroup> = equalTo(UnionTypeGroup)
val isMetaTypeGroup: Matcher<TypeGroup> = equalTo(MetaTypeGroup)
