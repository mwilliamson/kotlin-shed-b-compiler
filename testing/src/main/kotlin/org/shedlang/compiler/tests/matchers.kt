package org.shedlang.compiler.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.Identifier


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

fun isIdentifier(name: String) = has(Identifier::value, equalTo(name))

fun <T1, T2> isPair(first: Matcher<T1> = anythingOrNull, second: Matcher<T2>): Matcher<Pair<T1, T2>> {
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
