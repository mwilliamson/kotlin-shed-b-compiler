package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.stackir.loadModuleSet
import java.nio.file.Path

object LlvmBackend : Backend {
    internal fun archiveFiles(): List<Path> {
        return listOf(
            findRoot().resolve("stdlib-llvm/utf8proc/libutf8proc.a")
        )
    }

    internal fun objectFiles(): List<Path> {
        return listOf(
            findRoot().resolve("stdlib-llvm/Strings.o")
        )
    }

    override fun compile(moduleSet: ModuleSet, mainModule: ModuleName, target: Path) {
        val image = loadModuleSet(moduleSet)

        val file = createTempDir()
        try {
            val llPath = file.resolve("main.ll")
            val objectPath = file.resolve("main.o")
            val compiler = Compiler(image = image, moduleSet = moduleSet, irBuilder = LlvmIrBuilder())
            val llSource = compiler.compile(
                mainModule = mainModule
            )
            llPath.writeText(llSource)
            run(listOf(
                "llc", llPath.toString(),
                "-filetype", "obj",
                "-relocation-model", "pic",
                "-o", objectPath.toString()
            ))
            val gccCommand = listOf(
                "gcc",
                objectPath.toString(),
                "-o", target.toString()
            ) + (archiveFiles() + objectFiles()).map { path -> path.toString() }
            run(gccCommand)
        } finally {
            file.deleteRecursively()
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

    override fun run(path: Path, module: ModuleName): Int {
        throw UnsupportedOperationException("not implemented")
    }
}
