package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.findRoot
import java.nio.file.Path

internal fun executeLlvmInterpreter(path: Path): ExecutionResult {
    return org.shedlang.compiler.backends.tests.run(
        listOf("lli", "-extra-object=${findRoot().resolve("stdlib-llvm/Strings.o")}", path.toString()),
        workingDirectory = path.parent.toFile()
    )
}
