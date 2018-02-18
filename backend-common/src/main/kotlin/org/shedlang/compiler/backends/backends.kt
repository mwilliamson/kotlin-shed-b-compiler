package org.shedlang.compiler.backends

import org.shedlang.compiler.ModuleSet
import java.nio.file.Path

interface Backend {
    fun compile(moduleSet: ModuleSet, target: Path): Unit
    fun run(path: Path, module: List<String>): Int
}
