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
