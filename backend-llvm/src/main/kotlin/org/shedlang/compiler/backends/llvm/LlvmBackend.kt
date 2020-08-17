package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.formatModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.readPackage
import org.shedlang.compiler.stackir.loadModuleSet
import java.nio.file.Path

object LlvmBackend : Backend {
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
            compileBinary(llPath = llPath.toPath(), target = target, linkerFiles = compilationResult.linkerFiles)
        } finally {
            file.deleteRecursively()
        }
    }

    internal fun compileBinary(llPath: Path, target: Path, linkerFiles: List<String>) {
        val file = createTempDir()
        try {
            val objectPath = file.resolve("main.o")
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
            ) + linkerFiles.map { path -> findRoot().resolve(path).toString() }
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

    override fun run(path: Path, module: ModuleName, args: List<String>): Int {
        val command = listOf(path.toAbsolutePath().toString()) + args

        val process = ProcessBuilder(command)
            .inheritIO()
            .start()

        return process.waitFor()
    }

    override fun generateBindings(target: Path) {
        val moduleSet = readPackage(findRoot().resolve("stdlib"))

        generateModuleBindings(
            target = target,
            moduleSet = moduleSet,
            moduleName = listOf(Identifier("Core"), Identifier("Options"))
        )
    }

    private fun generateModuleBindings(target: Path, moduleSet: ModuleSet, moduleName: ModuleName) {
        val headerFileName = formatModuleName(moduleName) + ".h"

        val moduleType = moduleSet.moduleType(moduleName)!!
        val compiledModuleType = compiledType(moduleType) as CompiledObjectType

        val includeGuardName = "SHED__${formatModuleName(moduleName).replace(".", "_")}_H"
        val header = CHeader(
            includeGuardName = includeGuardName,
            statements = listOf(
                CVariableDeclaration(
                    name = nameForModuleValue(moduleName),
                    type = compiledModuleType.cType()
                )
            )
        )

        target.toFile().mkdirs()
        target.resolve(headerFileName).toFile().writeText(header.serialise())

    }
}
