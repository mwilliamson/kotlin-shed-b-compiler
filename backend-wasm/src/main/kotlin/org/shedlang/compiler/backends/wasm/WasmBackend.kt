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

            compile(
                temporaryDirectory = temporaryDirectory,
                result = compilationResult,
                target = target,
            )
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    internal fun compile(temporaryDirectory: Path, result: WasmCompilationResult, target: Path, noEntry: Boolean = false) {
        val objectFilePath = temporaryDirectory.resolve("program.o")
        objectFilePath.toFile().outputStream()
            .use { outputStream ->
                WasmBinaryFormat.writeObjectFile(
                    result.module,
                    outputStream,
                    tagValuesToInt = result.tagValuesToInt,
                )
            }

        val runtimeObjectFilePaths = listOf(
            "polyfills/stdlib.o",
            "polyfills/string.o",
            "deps/utf8proc.o",
            "shed.o",
            "strings.o",
            "stringbuilder.o",
            "modules/Core.Cast.o",
            "modules/Stdlib.Platform.StringBuilder.o",
            "modules/Stdlib.Platform.Strings.o",
            "shed_platform_effects.o",
            "modules/Core.IntToString.o",
            "modules/Stdlib.Platform.Process.o",
        ).map { path ->
            findRoot().resolve("backend-wasm/runtime/build").resolve(path).toString()
        }

        val ldArgs = mutableListOf("wasm-ld",  objectFilePath.toString(), "-o", target.toString())
        ldArgs.addAll(runtimeObjectFilePaths)
        if (noEntry) {
            ldArgs.add("--no-entry")
        }

        run(ldArgs)
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
        val command = generateWasmCommand(path, args)

        val process = ProcessBuilder(command)
            .inheritIO()
            .start()

        return process.waitFor()
    }

    override fun generateBindings(target: Path) {
    }

}
