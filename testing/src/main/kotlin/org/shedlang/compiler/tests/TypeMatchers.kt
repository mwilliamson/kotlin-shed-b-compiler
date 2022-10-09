package org.shedlang.compiler.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.*


fun isType(type: Type): Matcher<Type> = equalTo(type)

fun isFunctionType(
    typeLevelParameters: Matcher<List<TypeLevelParameter>> = anything,
    positionalParameters: Matcher<List<Type>> = anything,
    returnType: Matcher<Type> = anything,
    namedParameters: Matcher<Map<Identifier, Type>> = anything,
    effect: Matcher<Effect> = anything
): Matcher<Type> = cast(allOf(
    has(FunctionType::typeLevelParameters, typeLevelParameters),
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
): Matcher<TypeLevelValue> = cast(allOf(
    has(TypeAlias::name, name),
    has(TypeAlias::aliasedType, aliasedType)
))

fun isConstructedType(
    constructor: Matcher<TypeConstructor> = anything,
    args: Matcher<List<TypeLevelValue>> = anything,
    fields: Matcher<Collection<Field>?> = anything,
): Matcher<TypeLevelValue> = cast(allOf(
    has(ConstructedType2::constructor, constructor),
    has(ConstructedType2::args, args),
    has("fields", { type -> type.fields?.values }, fields),
))

fun isShapeType(
    shapeId: Matcher<Int> = anything,
    qualifiedName: Matcher<QualifiedName> = anything,
    name: Matcher<Identifier> = anything,
    tagValue: Matcher<TagValue?> = anything,
    fields: Matcher<Collection<Field>> = anything
): Matcher<TypeLevelValue> = cast(allOf(
    has(ShapeType::shapeId, shapeId),
    has(ShapeType::qualifiedName, qualifiedName),
    has(ShapeType::name, name),
    has(ShapeType::tagValue, tagValue),
    has("fields", { type -> type.fields.values }, fields),
))

fun isField(
    name: Matcher<Identifier> = anything,
    type: Matcher<Type> = anything,
    shapeId: Matcher<Int> = anything
) = allOf(
    has(Field::name, name),
    has(Field::type, type),
    has(Field::shapeId, shapeId)
)

fun isUnionType(
    name: Matcher<Identifier> = anything,
    tag: Matcher<Tag> = anything,
    members: Matcher<List<Type>> = anything
): Matcher<TypeLevelValue> = cast(allOf(
    has(UnionType::name, name),
    has(UnionType::tag, tag),
    has(UnionType::members, members)
))

fun isTag(moduleName: List<String>, name: String) = isTag(QualifiedName.topLevelType(moduleName, name))

fun isTag(qualifiedName: QualifiedName) = has(Tag::qualifiedName, equalTo(qualifiedName))

fun isTagValue(tag: Matcher<Tag>, value: String) = cast(allOf(
    has(TagValue::tag, tag),
    has(TagValue::value, isIdentifier(value))
))

val isAnyType: Matcher<TypeLevelValue> = cast(equalTo(AnyType))
val isNothingType: Matcher<TypeLevelValue> = cast(equalTo(NothingType))
val isUnitType: Matcher<TypeLevelValue> = cast(equalTo(UnitType))
val isIntType: Matcher<TypeLevelValue> = cast(equalTo(IntType))
val isBoolType: Matcher<TypeLevelValue> = cast(equalTo(BoolType))
val isStringType: Matcher<TypeLevelValue> = cast(equalTo(StringType))

fun isEffectType(effect: Matcher<Effect>) = isTypeLevelValueType(cast(effect))
fun isMetaType(value: Matcher<Type>): Matcher<TypeLevelValue> = isTypeLevelValueType(cast(value))
fun isTypeLevelValueType(value: Matcher<TypeLevelValue>): Matcher<TypeLevelValue> = cast(has(TypeLevelValueType::value, value))

fun isTypeConstructor(
    parameters: Matcher<List<TypeLevelParameter>>,
    genericType: Matcher<TypeLevelValue> = anything
): Matcher<TypeLevelValue> = cast(allOf(
    has(TypeConstructor::parameters, parameters),
    has(TypeConstructor::genericType, cast(genericType))
))

fun isTypeParameter(
    name: Matcher<Identifier> = anything,
    variance: Matcher<Variance> = anything
): Matcher<TypeLevelValue> = cast(allOf(
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

fun isEffectUnion(members: Matcher<List<Effect>>): Matcher<TypeLevelValue> = cast(has(EffectUnion::members, members))

val isIoEffect: Matcher<Effect> = equalTo(IoEffect)

fun isEffect(effect: Effect): Matcher<Effect> = equalTo(effect)
