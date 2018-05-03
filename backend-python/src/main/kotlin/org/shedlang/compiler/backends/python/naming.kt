package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ast.Identifier

internal fun pythoniseName(originalName: Identifier): String {
    return pythoniseName(originalName.value)
}

private fun pythoniseName(originalName: String): String {
    val casedName = if (originalName[0].isUpperCase()) {
        originalName
    } else {
        camelCaseToSnakeCase(originalName)
    }
    return if (isKeyword(casedName)) {
        casedName + "_"
    } else {
        casedName
    }
}


private fun camelCaseToSnakeCase(name: String): String {
    return Regex("\\p{javaUpperCase}").replace(name, { char -> "_" + char.value.toLowerCase() })
}

private fun isKeyword(name: String): Boolean {
    return pythonKeywords.contains(name)
}

private val pythonKeywords = setOf(
    "False",
    "None",
    "True",
    "and",
    "as",
    "assert",
    "break",
    "class",
    "continue",
    "def",
    "del",
    "elif",
    "else",
    "except",
    "exec",
    "finally",
    "for",
    "from",
    "global",
    "if",
    "import",
    "in",
    "is",
    "lambda",
    "nonlocal",
    "not",
    // object isn't a keyword, but we rely on it being available
    "object",
    "or",
    "pass",
    // We add a __future__ statement for print functions, so we don't need to
    // consider print a keyword in any version of Python
    // "print",
    "raise",
    "return",
    "try",
    "while",
    "with",
    "yield"
)
