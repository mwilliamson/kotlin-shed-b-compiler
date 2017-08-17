package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.Source

internal open class ParseError(message: String, val location: Source): Exception(message)
