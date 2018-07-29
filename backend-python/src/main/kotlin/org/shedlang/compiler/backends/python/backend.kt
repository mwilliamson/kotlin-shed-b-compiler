package org.shedlang.compiler.backends.python

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.NodeSource
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.backends.python.ast.PythonAssignmentNode
import org.shedlang.compiler.backends.python.ast.PythonStringLiteralNode
import org.shedlang.compiler.backends.python.ast.PythonVariableReferenceNode
import org.shedlang.compiler.backends.readResourceText
import org.shedlang.compiler.backends.resourceStream
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path

val topLevelPythonPackageName = "shed"

val backend = object: Backend {
    override fun compile(moduleSet: ModuleSet, target: Path) {
        for (module in moduleSet.modules) {
            when (module) {
                is Module.Shed -> {
                    val pythonModule = compileModule(module)
                    writeModule(target, pythonModule)
                }
                // TODO: remove duplication with JavaScript backend
                is Module.Native -> {
                    moduleWriter(target, shedModuleNameToPythonModuleName(module.name)).use { writer ->
                        nativeModuleSource(module).reader().use { reader ->
                            reader.copyTo(writer)
                        }
                    }
                }
            }
        }
        writeModule(target, builtinModule())
    }

    private fun nativeModuleSource(module: Module.Native): InputStream {
        val resourcePrefix = "org/shedlang/compiler/backends/python/modules/"
        val resourceName = resourcePrefix + module.name.map { part -> part.value }.joinToString("/") + ".py"
        return resourceStream(resourceName)
    }

    private fun writeModule(target: Path, module: PythonModule) {
        moduleWriter(target, module.name).use { writer ->
            writer.write("# encoding=utf-8\n")
            writer.write(module.source)
        }
    }

    private fun moduleWriter(target: Path, moduleName: List<String>): OutputStreamWriter {
        val modulePath = moduleName.joinToString(File.separator) + ".py"
        val destination = target.resolve(modulePath)
        val pythonPackage = destination.parent
        pythonPackage.toFile().mkdirs()
        addInitFiles(target, pythonPackage)

        return destination.toFile().writer(StandardCharsets.UTF_8)
    }

    override fun run(path: Path, module: List<String>): Int {
        val process = ProcessBuilder("python3", "-m", topLevelPythonPackageName + "." + module.joinToString("."))
            .inheritIO()
            .directory(path.toFile())
            .start()
        return process.waitFor()
    }
}

fun compile(frontendResult: ModuleSet, target: Path) {
    backend.compile(frontendResult, target = target)
}

private fun addInitFiles(base: Path, pythonPackage: Path) {
    var currentPackage = pythonPackage
    while (base != currentPackage) {
        currentPackage.resolve("__init__.py").toFile().createNewFile()
        currentPackage = currentPackage.parent
    }
}

private fun compileModule(module: Module.Shed): PythonModule {
    val generateCode = generateCode(module.node, module.references)
    val builtins = """
        from __future__ import print_function

        from shed.builtins import (
            int_to_string,
            list,
            print,
            partial as _partial,
            symbol_factory as _symbol_factory,
        )

        _symbol = _symbol_factory()
    """.trimIndent()

    // TODO: push module name generation into code generator
    val moduleName = PythonAssignmentNode(
        target = PythonVariableReferenceNode(
            "module_name",
            source = NodeSource(module.node)
        ),
        expression = PythonStringLiteralNode(
            module.name.map(Identifier::value).joinToString("."),
            source = NodeSource(module.node)
        ),
        source = NodeSource(module.node)
    )
    val contents = builtins + "\n" + serialise(moduleName) + "\n" + serialise(generateCode) + "\n"
    val main = if (module.hasMain()) {
        // TODO: avoid _shed_main collision
        """
            def _shed_main():
                import sys as sys
                exit_code = main()
                if exit_code is not None:
                    sys.exit(exit_code)

            if __name__ == "__main__":
                _shed_main()
        """.trimIndent()
    } else {
        ""
    }
    return PythonModule(
        name = shedModuleNameToPythonModuleName(module.name),
        source = contents + main
    )
}

internal fun shedModuleNameToPythonModuleName(moduleName: List<Identifier>) =
    listOf(topLevelPythonPackageName) + moduleName.map(Identifier::value)

private class PythonModule(val name: List<String>, val source: String)

private fun builtinModule(): PythonModule {
    val contents = readResourceText("org/shedlang/compiler/backends/python/modules/builtins.py")
    return PythonModule(
        name = listOf("shed", "builtins"),
        source = contents
    )
}
