package org.shedlang.compiler

fun <T: Any> T?.orElseThrow(exception: Exception): T {
    if (this == null) {
        throw exception
    } else {
        return this
    }
}
