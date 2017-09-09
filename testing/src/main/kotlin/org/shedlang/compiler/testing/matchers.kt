package org.shedlang.compiler.testing

import com.natpryce.hamkrest.*


fun <T> allOf(vararg matchers: Matcher<T>) : Matcher<T> {
    return matchers.reduce { first, second -> first and second }
}

fun <K, V> isMap(vararg matchers: Pair<K, Matcher<V>>): Matcher<Map<K, V>> {
    val entryMatchers = matchers.map({ entry ->
        allOf(
            has(Map.Entry<K, V>::key, equalTo(entry.first)),
            has(Map.Entry<K, V>::value, entry.second)
        )
    })
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
