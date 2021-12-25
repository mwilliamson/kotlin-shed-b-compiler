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
    tagValue: Matcher<TagValue?> = anything,
    staticParameters: Matcher<List<StaticParameter>> = anything,
    staticArguments: Matcher<List<StaticValue>> = anything,
    fields: Matcher<Collection<Field>> = anything
): Matcher<StaticValue> = cast(allOf(
    has(ShapeType::shapeId, shapeId),
    has(ShapeType::name, name),
    has(ShapeType::tagValue, tagValue),
    has(ShapeType::staticParameters, staticParameters),
    has(ShapeType::staticArguments, staticArguments),
    has("allFields", { type -> type.fields.values }, fields),
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
    tag: Matcher<Tag> = anything,
    staticArguments: Matcher<List<StaticValue>> = anything,
    members: Matcher<List<Type>> = anything
): Matcher<StaticValue> = cast(allOf(
    has(UnionType::name, name),
    has(UnionType::tag, tag),
    has(UnionType::staticArguments, staticArguments),
    has(UnionType::members, members)
))

fun isTag(moduleName: List<String>, name: String) = cast(allOf(
    has(Tag::moduleName, equalTo(moduleName.map(::Identifier))),
    has(Tag::name, isIdentifier(name))
))

fun isTagValue(tag: Matcher<Tag>, value: String) = cast(allOf(
    has(TagValue::tag, tag),
    has(TagValue::value, isIdentifier(value))
))

val isAnyType: Matcher<StaticValue> = cast(equalTo(AnyType))
val isNothingType: Matcher<StaticValue> = cast(equalTo(NothingType))
val isUnitType: Matcher<StaticValue> = cast(equalTo(UnitType))
val isIntType: Matcher<StaticValue> = cast(equalTo(IntType))
val isBoolType: Matcher<StaticValue> = cast(equalTo(BoolType))
val isStringType: Matcher<StaticValue> = cast(equalTo(StringType))

fun isEffectType(effect: Matcher<Effect>) = isStaticValueType(cast(effect))
fun isMetaType(value: Matcher<Type>): Matcher<StaticValue> = isStaticValueType(cast(value))
fun isStaticValueType(value: Matcher<StaticValue>): Matcher<StaticValue> = cast(has(StaticValueType::value, value))

fun isParameterizedStaticValue(
    parameters: Matcher<List<StaticParameter>>,
    value: Matcher<StaticValue> = anything
): Matcher<StaticValue> = cast(allOf(
    has(ParameterizedStaticValue::parameters, parameters),
    has(ParameterizedStaticValue::value, cast(value))
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

fun isDiscriminator(tagValue: Matcher<TagValue>, targetType: Matcher<Type> = anything): Matcher<Discriminator> = allOf(
    has(Discriminator::tagValue, tagValue),
    has(Discriminator::targetType, targetType)
)

fun isUserDefinedEffect(
    name: Matcher<Identifier>,
    operations: Matcher<Map<Identifier, FunctionType>> = anything
): Matcher<Effect> {
    return cast(allOf(
        has(UserDefinedEffect::name, name),
        has(UserDefinedEffect::operations, operations)
    ))
}

fun isEffectUnion(members: Matcher<List<Effect>>): Matcher<StaticValue> = cast(has(EffectUnion::members, members))

val isIoEffect: Matcher<Effect> = equalTo(IoEffect)

fun isEffect(effect: Effect): Matcher<Effect> = equalTo(effect)
