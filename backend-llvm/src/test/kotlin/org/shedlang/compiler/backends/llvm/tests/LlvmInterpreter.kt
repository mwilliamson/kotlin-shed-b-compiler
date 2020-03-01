package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.findRoot
import java.nio.file.Path

internal fun executeLlvmInterpreter(path: Path, includeStrings: Boolean = false): ExecutionResult {
    val extraObjectArgs = if (includeStrings) {
        listOf("-extra-object=${findRoot().resolve("stdlib-llvm/Strings.o")}")
    } else {
        listOf()
    }
    return org.shedlang.compiler.backends.tests.run(
        listOf("lli") + extraObjectArgs + listOf(path.toString()),
        workingDirectory = path.parent.toFile()
    )
}
