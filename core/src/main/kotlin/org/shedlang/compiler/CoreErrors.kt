package org.shedlang.compiler

import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.types.Type

/**
 * This indicates a bug in the compiler or its calling code
 */
open class CompilerError(message: String, val source: Source) : Exception(message)

/**
 * This indicates a bug in the source code that is being compiled
 */

open class SourceError(message: String?, val source: Source): Exception(message)

open class TypeCheckError(message: String?, source: Source) : SourceError(message, source)

open class CannotUnionTypesError(val left: Type, val right: Type, source: Source)
    : TypeCheckError("cannot union ${left.shortDescription} with ${right.shortDescription}", source = source)
