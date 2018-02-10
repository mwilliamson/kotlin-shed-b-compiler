package org.shedlang.compiler.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.types.*


val anythingOrNull = object : Matcher<Any?> {
    override fun invoke(actual: Any?): MatchResult = MatchResult.Match
    override val description: String get() = "anything"
    override val negatedDescription: String get() = "nothing"
}

fun <T> allOf(vararg matchers: Matcher<T>) : Matcher<T> {
    return matchers.reduce { first, second -> first and second }
}

fun <K, V> isMap(vararg matchers: Pair<K, Matcher<V>>): Matcher<Map<K, V>> {
    val entryMatchers = matchers.map({ entry -> allOf(
        has(Map.Entry<K, V>::key, equalTo(entry.first)),
        has(Map.Entry<K, V>::value, entry.second)
    ) })
    return has<Map<K, V>, Iterable<Map.Entry<K, V>>>(
        name = "entries",
        feature = { map -> map.entries },
        featureMatcher = isSequence(*entryMatchers.toTypedArray())
    )
}

fun <T> isSequence(vararg matchers: Matcher<T>) : Matcher<Iterable<T>> {
    return object : Matcher.Primitive<Iterable<T>>() {
        override fun invoke(actual: Iterable<T>): MatchResult {
            val actualValues = actual.toList()
            val elementResults = actualValues.zip(matchers, {element, matcher -> matcher.invoke(element) })
            val firstMismatch = elementResults.withIndex().firstOrNull { result -> result.value is MatchResult.Mismatch }
            if (firstMismatch != null) {
                return MatchResult.Mismatch(
                        "item " + firstMismatch.index + ": " + (firstMismatch.value as MatchResult.Mismatch).description
                )
            } else if (actualValues.size != matchers.size) {
                return MatchResult.Mismatch("had " + actualValues.size + " elements")
            } else {
                return MatchResult.Match
            }
        }

        override val description: String
            get() {
                return "is sequence:\n" + matchers.mapIndexed { index, matcher -> indent("$index: ${matcher.description}") + "\n" }.joinToString("")
            }

    }
}

fun <T1, T2> isPair(first: Matcher<T1>, second: Matcher<T2>): Matcher<Pair<T1, T2>> {
    return allOf(
        has(Pair<T1, T2>::first, first),
        has(Pair<T1, T2>::second, second)
    )
}

private fun indent(value: String): String {
    val indentation = "  "
    val indexWidth = 2
    return indentation + value.replace("\n", "\n" + indentation + " ".repeat(indexWidth))
}

internal fun isType(type: Type): Matcher<Type> = equalTo(type)

internal fun isFunctionType(
    arguments: Matcher<List<Type>> = anything,
    returnType: Matcher<Type> = anything,
    namedArguments: Matcher<Map<String, Type>> = anything,
    effect: Matcher<Effect> = anything
): Matcher<Type> = cast(allOf(
    has(FunctionType::positionalArguments, arguments),
    has(FunctionType::namedArguments, namedArguments),
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
