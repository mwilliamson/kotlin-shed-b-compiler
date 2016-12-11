package org.shedlang.compiler.parser

internal data class Token<T>(val characterIndex: Int, val tokenType: T, val value: String)
