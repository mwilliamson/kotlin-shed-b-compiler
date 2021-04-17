package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.createTempDirectory
import org.shedlang.compiler.backends.llvm.LlvmBackend
import org.shedlang.compiler.backends.tests.ExecutionResult
import java.nio.file.Path

internal fun executeLlvmIr(path: Path, linkerFiles: List<String>, args: List<String>): ExecutionResult {
    val temporaryDirectory = createTempDirectory()
    try {
        val binaryPath = temporaryDirectory.resolve("binary")
        LlvmBackend.compileBinary(llPath = path, target = binaryPath, linkerFiles = linkerFiles)
        return org.shedlang.compiler.backends.tests.run(
            listOf(binaryPath.toString()) + args,
            workingDirectory = path.parent.toFile()
        )
    } finally {
        temporaryDirectory.toFile().deleteRecursively()
    }
}
