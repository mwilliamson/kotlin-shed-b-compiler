package org.shedlang.compiler.parser

import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.typechecker.SourceError

internal open class ParseError(message: String, source: Source): SourceError(message, source)

internal class InconsistentBranchTerminationError(source: Source): ParseError(
    message = "inconsistent branch termination: either all branches must terminate, or all branches must not terminate",
    source = source
)
