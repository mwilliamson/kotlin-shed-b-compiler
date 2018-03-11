package org.shedlang.compiler.tests

import com.natpryce.hamkrest.Matcher

fun <T> Iterable<T>.firstIndex(matcher: Matcher<T>): Int {
    val result = this.indexOfFirst(matcher.asPredicate())
    if (result == -1) {
        throw Exception("Could not find matching element: " + matcher.description)
    } else {
        return result
    }
}
