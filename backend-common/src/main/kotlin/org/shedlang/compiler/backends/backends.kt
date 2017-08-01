package org.shedlang.compiler.backends

import org.shedlang.compiler.FrontEndResult
import java.nio.file.Path

interface Backend {
    fun compile(frontEndResult: FrontEndResult, target: Path): Unit
}
