package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.stackir.loadModuleSet
import java.nio.file.Path

object WasmBackend : Backend {
    override fun compile(moduleSet: ModuleSet, mainModule: ModuleName, target: Path) {
        val image = loadModuleSet(moduleSet)

        val temporaryDirectory = createTempDir()
        try {
            val compilationResult = WasmCompiler(image = image, moduleSet = moduleSet).compile(
                mainModule = mainModule
            )
            val watPath = temporaryDirectory.toPath().resolve("program.wat")
            watPath.toFile().writeText(compilationResult.wat)

            run(listOf("wat2wasm", watPath.toString(), "-o", target.toString()))
        } finally {
            temporaryDirectory.deleteRecursively()
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
        throw UnsupportedOperationException("not implemented")
    }

    override fun generateBindings(target: Path) {
    }

}
