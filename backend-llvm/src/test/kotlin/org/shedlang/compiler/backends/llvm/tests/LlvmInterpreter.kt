package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.llvm.LlvmBackend
import org.shedlang.compiler.backends.tests.ExecutionResult
import java.nio.file.Path

internal fun executeLlvmInterpreter(path: Path, includeStrings: Boolean = false): ExecutionResult {
    val extraObjectArgs = if (includeStrings) {
        listOf(
            LlvmBackend.archiveFiles().map { path -> "-extra-archive=${path}" },
            LlvmBackend.objectFiles().map { path -> "-extra-object=${path}" }
        ).flatten()
    } else {
        listOf()
    }
    return org.shedlang.compiler.backends.tests.run(
        listOf("lli") + extraObjectArgs + listOf(path.toString()),
        workingDirectory = path.parent.toFile()
    )
}
