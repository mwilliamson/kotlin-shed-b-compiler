package org.shedlang.compiler

fun <T: Any?> T?.orElseThrow(exception: Exception): T {
    if (this == null) {
        throw exception
    } else {
        return this
    }
}

internal fun Iterable<Boolean>.all(): Boolean {
    for (element in this) {
        if (!element) {
            return false
        }
    }
    return true
}
