package org.shedlang.compiler.backends.python

import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.Module
import org.shedlang.compiler.backends.Backend
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path

val topLevelPythonPackageName = "shed"

val backend = object: Backend {
    override fun compile(frontEndResult: FrontEndResult, target: Path) {
        val pythonModules = frontEndResult.modules.map(::compileModule)

        pythonModules.forEach({ module ->
            writeModule(target, module)
        })
        writeModule(target, builtinModule)
    }

    private fun writeModule(target: Path, module: PythonModule) {
        val modulePath = module.name.joinToString(File.separator) + ".py"
        val destination = target.resolve(modulePath)
        val pythonPackage = destination.parent
        pythonPackage.toFile().mkdirs()
        addInitFiles(target, pythonPackage)

        destination.toFile().writer(StandardCharsets.UTF_8).use { writer ->
            writer.write("# encoding=utf-8\n")
            writer.write(module.source)
        }
    }

    override fun run(path: Path, module: List<String>): Int {
        val process = ProcessBuilder("python3", "-m", "shed." + module.joinToString("."))
            .inheritIO()
            .directory(path.toFile())
            .start()
        return process.waitFor()
    }
}

fun compile(frontendResult: FrontEndResult, target: Path) {
    backend.compile(frontendResult, target = target)
}

private fun addInitFiles(base: Path, pythonPackage: Path) {
    var currentPackage = pythonPackage
    while (base != currentPackage) {
        currentPackage.resolve("__init__.py").toFile().createNewFile()
        currentPackage = currentPackage.parent
    }
}

private fun compileModule(module: Module): PythonModule {
    val generateCode = generateCode(module.node, module.references)
    val builtins = """
        from __future__ import print_function

        from shed.builtins import (
            int_to_string,
            list,
            print,
            for_each,
            map,
            partial as _partial,
        )
    """.trimIndent()
    val contents = builtins + "\n" + serialise(generateCode) + "\n"
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
        name = listOf(topLevelPythonPackageName) + module.name,
        source = contents + main
    )
}

private class PythonModule(val name: List<String>, val source: String)

private val builtinModule = PythonModule(
    name = listOf("shed", "builtins"),
    source = """
         from __future__ import print_function

         from functools import partial

         int_to_string = str

         _list = list
         def list(*args):
             return _list(args)

         _print = print
         def print(value):
             _print(value, end="")

         def for_each(func, elements):
             for element in elements:
                 func(element)

         def map(func, elements):
             result = []
             for element in elements:
                 result.append(func(element))
             return result

    """.trimIndent()
)
