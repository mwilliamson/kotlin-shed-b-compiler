package org.shedlang.compiler.backends

fun serialiseCStringLiteral(value: String): String {
    val escapedValue = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\"", "\\\"")

    return "\"" + escapedValue + "\""
}

// TODO: extracted from Python, add tests

class SubExpressionSerialiser<T>(
    private val precedence: (T) -> Int,
    private val serialise: (T) -> String
) {
    fun serialiseSubExpression(
        parentNode: T,
        node: T,
        associative: Boolean
    ): String {
        val parentPrecedence = precedence(parentNode)
        val serialised = serialise(node)
        val subPrecedence = precedence(node)
        if (parentPrecedence > subPrecedence || parentPrecedence == subPrecedence && !associative) {
            return "(" + serialised + ")"
        } else {
            return serialised
        }
    }
}
