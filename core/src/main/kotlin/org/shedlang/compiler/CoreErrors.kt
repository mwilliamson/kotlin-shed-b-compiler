package org.shedlang.compiler

import org.shedlang.compiler.ast.Source

open class CompilerError(message: String, val source: Source) : Exception(message)
