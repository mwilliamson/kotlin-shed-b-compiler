package org.shedlang.compiler.frontend.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.anythingOrNull
import org.shedlang.compiler.tests.isMap
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.*

internal fun isType(type: Type): Matcher<Type> = equalTo(type)

internal fun isFunctionType(
    positionalParameters: Matcher<List<Type>> = anything,
    returnType: Matcher<Type> = anything,
    namedParameters: Matcher<Map<String, Type>> = anything,
    effect: Matcher<Effect> = anything
): Matcher<Type> = cast(allOf(
    has(FunctionType::positionalParameters, positionalParameters),
    has(FunctionType::namedParameters, namedParameters),
    has(FunctionType::returns, returnType),
    has(FunctionType::effect, effect)
))

internal fun isShapeType(
    name: Matcher<String> = anything,
    typeArguments: Matcher<List<Type>> = anything,
    shapeId: Matcher<Int> = anything,
    tagField: Matcher<TagField?> = anythingOrNull,
    tagValue: Matcher<TagValue?> = anythingOrNull
): Matcher<Type> = cast(allOf(
    has(ShapeType::name, name),
    has(ShapeType::typeArguments, typeArguments),
    has(ShapeType::shapeId, shapeId),
    has(ShapeType::declaredTagField, tagField),
    has(ShapeType::tagValue, tagValue)
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
    members: Matcher<List<Type>> = anything,
    tagField: Matcher<TagField> = anything
): Matcher<Type> = cast(allOf(
    has(UnionType::name, name),
    has(UnionType::typeArguments, typeArguments),
    has(UnionType::members, members),
    has(UnionType::declaredTagField, tagField)
))

internal val isAnyType: Matcher<TypeGroup> = cast(equalTo(AnyType))
internal val isNothingType: Matcher<TypeGroup> = cast(equalTo(NothingType))
internal val isUnitType: Matcher<TypeGroup> = cast(equalTo(UnitType))
internal val isIntType: Matcher<TypeGroup> = cast(equalTo(IntType))
internal val isBoolType: Matcher<TypeGroup> = cast(equalTo(BoolType))
internal val isStringType: Matcher<TypeGroup> = cast(equalTo(StringType))

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

internal val isUnionTypeGroup: Matcher<TypeGroup> = equalTo(UnionTypeGroup)
internal val isMetaTypeGroup: Matcher<TypeGroup> = equalTo(MetaTypeGroup)

fun isTag(name: Matcher<String>, tagId: Matcher<Int>): Matcher<TagField> {
    return allOf(
        has(TagField::name, name),
        has(TagField::tagFieldId, tagId)
    )
}

fun isTagValue(tagField: Matcher<TagField>, tagValueId: Matcher<Int>): Matcher<TagValue> {
    return allOf(
        has(TagValue::tagField, tagField),
        has(TagValue::tagValueId, tagValueId)
    )
}

inline fun <reified T : Throwable> throwsException(exceptionCriteria: Matcher<T>? = null): Matcher<() -> Unit> {
    val exceptionName = T::class.qualifiedName

    return object : Matcher<() -> Unit> {
        override fun invoke(actual: () -> Unit): MatchResult =
            try {
                actual()
                MatchResult.Mismatch("did not throw")
            } catch (e: Throwable) {
                if (e is T) {
                    exceptionCriteria?.invoke(e) ?: MatchResult.Match
                } else {
                    throw e
                }
            }

        override val description: String get() = "throws ${exceptionName}${exceptionCriteria?.let { " that ${describe(it)}" } ?: ""}"
        override val negatedDescription: String get() = "does not throw ${exceptionName}${exceptionCriteria?.let { " that ${describe(it)}" } ?: ""}"
    }
}
