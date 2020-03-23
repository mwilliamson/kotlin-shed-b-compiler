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
            findRoot().resolve("stdlib-llvm/gc-8.0.4/.libs/libgc.a")
        )
    }

    internal fun stringArchiveFiles(): List<Path> {
        return listOf(
            findRoot().resolve("stdlib-llvm/utf8proc/libutf8proc.a")
        )
    }

    internal fun stringObjectFiles(): List<Path> {
        return listOf(
            findRoot().resolve("stdlib-llvm/Strings.o")
        )
    }

    override fun compile(moduleSet: ModuleSet, mainModule: ModuleName, target: Path) {
        val image = loadModuleSet(moduleSet)

        val file = createTempDir()
        try {
            val llPath = file.resolve("main.ll")
            val compiler = Compiler(image = image, moduleSet = moduleSet, irBuilder = LlvmIrBuilder())
            val compilationResult = compiler.compile(
                mainModule = mainModule
            )
            llPath.writeText(compilationResult.llvmIr)
            compileBinary(llPath = llPath.toPath(), target = target, includeStrings = true)
        } finally {
            file.deleteRecursively()
        }
    }

    internal fun compileBinary(llPath: Path, target: Path, includeStrings: Boolean) {
        val file = createTempDir()
        try {
            val objectPath = file.resolve("main.o")
            run(listOf(
                "llc", llPath.toString(),
                "-filetype", "obj",
                "-relocation-model", "pic",
                "-o", objectPath.toString()
            ))
            val stringsCode = if (includeStrings) stringObjectFiles() + stringArchiveFiles() else listOf()
            val gccCommand = listOf(
                "gcc",
                objectPath.toString(),
                "-o", target.toString()
            ) + (archiveFiles() + stringsCode).map { path -> path.toString() }
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
