package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.backends.createTempDirectory
import org.shedlang.compiler.backends.wasm.wasm.WasmBinaryFormat
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.stackir.loadModuleSet
import java.nio.file.Path

object WasmBackend : Backend {
    override fun compile(moduleSet: ModuleSet, mainModule: ModuleName, target: Path) {
        val image = loadModuleSet(moduleSet)

        val temporaryDirectory = createTempDirectory()
        try {
            val compilationResult = WasmCompiler(image = image, moduleSet = moduleSet).compile(
                mainModule = mainModule
            )

            val objectFilePath = temporaryDirectory.resolve("program.o")
            objectFilePath.toFile().outputStream()
                .use { outputStream ->
                    WasmBinaryFormat.writeObjectFile(
                        compilationResult.module,
                        outputStream,
                        tagValuesToInt = compilationResult.tagValuesToInt,
                    )
                }

            val runtimeObjectFilePaths = listOf("strings.o", "modules/Core.IntToString.o").map { path ->
                findRoot().resolve("backend-wasm/runtime/build").resolve(path).toString()
            }
            run(listOf("wasm-ld",  objectFilePath.toString(), "-o", target.toString()) + runtimeObjectFilePaths)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    private fun run(args: List<String>) {
        val process = ProcessBuilder(args)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw Exception("${args[0]} exit code: $exitCode")
        }
    }

    override fun run(path: Path, module: ModuleName, args: List<String>): Int {
        val command = listOf("wasmtime", path.toAbsolutePath().toString()) + args

        val process = ProcessBuilder(command)
            .inheritIO()
            .start()

        return process.waitFor()
    }

    override fun generateBindings(target: Path) {
    }

}
