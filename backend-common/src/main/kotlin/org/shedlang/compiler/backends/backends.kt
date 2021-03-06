package org.shedlang.compiler.backends

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ModuleName
import java.nio.file.Path

interface Backend {
    fun compile(moduleSet: ModuleSet, mainModule: ModuleName, target: Path): Unit
    fun run(path: Path, module: ModuleName, args: List<String>): Int
    fun generateBindings(target: Path)
}
