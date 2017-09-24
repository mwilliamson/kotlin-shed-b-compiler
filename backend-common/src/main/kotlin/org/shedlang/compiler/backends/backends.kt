package org.shedlang.compiler.backends

import org.shedlang.compiler.FrontEndResult
import java.nio.file.Path

interface Backend {
    fun compile(frontEndResult: FrontEndResult, target: Path): Unit
    fun run(path: Path, module: List<String>): Int
}
