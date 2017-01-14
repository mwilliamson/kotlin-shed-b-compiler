package org.shedlang.compiler.parser

internal data class Token<T>(val characterIndex: Int, val tokenType: T, val value: String) {
    fun describe(): String {
        return describeToken(tokenType, value)
    }
}

internal fun <T> describeToken(tokenType: T, value: String) : String {
    return "${tokenType}: ${value}"
}
