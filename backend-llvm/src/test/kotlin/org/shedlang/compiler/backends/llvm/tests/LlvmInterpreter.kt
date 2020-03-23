package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.llvm.LlvmBackend
import org.shedlang.compiler.backends.tests.ExecutionResult
import java.nio.file.Path

internal fun executeLlvmInterpreter(path: Path, linkerFiles: List<String>): ExecutionResult {
    val temporaryDirectory = createTempDir()
    try {
        val binaryPath = temporaryDirectory.resolve("binary")
        LlvmBackend.compileBinary(llPath = path, target = binaryPath.toPath(), linkerFiles = linkerFiles)
        return org.shedlang.compiler.backends.tests.run(
            listOf(binaryPath.toString()),
            workingDirectory = path.parent.toFile()
        )
    } finally {
        temporaryDirectory.deleteRecursively()
    }
}
