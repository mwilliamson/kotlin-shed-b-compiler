package org.shedlang.compiler.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.typechecker.FunctionType
import org.shedlang.compiler.typechecker.Type


fun <T> allOf(vararg matchers: Matcher<T>) : Matcher<T> {
    return matchers.reduce { first, second -> first and second }
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
    arguments: Matcher<List<Type>>,
    returnType: Matcher<Type>
): Matcher<Type> = cast(allOf(
    has(FunctionType::arguments, arguments),
    has(FunctionType::returns, returnType)
))
