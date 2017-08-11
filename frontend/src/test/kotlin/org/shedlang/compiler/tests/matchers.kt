package org.shedlang.compiler.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.types.*


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

private fun indent(value: String): String {
    val indentation = "  "
    val indexWidth = 2
    return indentation + value.replace("\n", "\n" + indentation + " ".repeat(indexWidth))
}

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
    typeArguments: Matcher<List<Type>>
): Matcher<Type> = cast(allOf(
    has(ShapeType::name, name),
    has(ShapeType::typeArguments, typeArguments)
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

internal fun isTypeFunction(
    parameters: Matcher<List<TypeParameter>>,
    type: Matcher<Type>
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
