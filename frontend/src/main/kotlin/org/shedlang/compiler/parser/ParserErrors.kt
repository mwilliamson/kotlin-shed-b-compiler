package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.typechecker.SourceError

internal open class ParseError(message: String, source: Source): SourceError(message, source)