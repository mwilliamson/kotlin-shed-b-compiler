package org.shedlang.compiler.frontend.parser

import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.SourceError

internal open class ParseError(message: String, source: Source): SourceError(message, source)

internal open class InvalidUnicodeScalar(
    source: Source,
    message: String
) : ParseError(message, source = source)

internal class UnrecognisedEscapeSequenceError(
    val escapeSequence: String,
    source: Source
) : InvalidUnicodeScalar(
    source = source,
    message = "Unrecognised escape sequence"
)

internal class InvalidUnicodeScalarLiteral(message: String, source: Source) : ParseError(
    message = message,
    source = source
)

internal class InconsistentBranchTerminationError(source: Source): ParseError(
    message = "inconsistent branch termination: either all branches must terminate, or all branches must not terminate",
    source = source
)

internal class PositionalArgumentAfterNamedArgumentError(source: Source): ParseError(
    message = "positional arguments are not allowed after named arguments",
    source = source
)


internal class PositionalParameterAfterNamedParameterError(source: Source): ParseError(
    message = "positional parameters are not allowed after named parameters",
    source = source
)
