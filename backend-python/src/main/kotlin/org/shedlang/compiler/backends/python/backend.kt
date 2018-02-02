package org.shedlang.compiler.backends.python

import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.Module
import org.shedlang.compiler.backends.Backend
import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Path

val topLevelPythonPackageName = "shed"

val backend = object: Backend {
    override fun compile(frontEndResult: FrontEndResult, target: Path) {
        frontEndResult.modules.forEach({ module ->
            val modulePath = module.name.joinToString(File.separator) + ".py"
            val destination = target.resolve(topLevelPythonPackageName).resolve(modulePath)
            val pythonPackage = destination.parent
            pythonPackage.toFile().mkdirs()
            addInitFiles(target, pythonPackage)

            destination.toFile().writer(StandardCharsets.UTF_8).use { writer ->
                compileModule(
                    module = module,
                    writer = writer
                )
            }
        })
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

private fun compileModule(module: Module, writer: Writer) {
    val generateCode = generateCode(module.node, module.references)
    val stdlib = """
        from __future__ import print_function

        int_to_string = str
        _print = print
        def print(value):
            _print(value, end="")
    """.trimIndent()
    val contents = stdlib + "\n" + serialise(generateCode) + "\n"
    writer.write(contents)
    if (module.hasMain()) {
        // TODO: avoid _shed_main collision
        writer.write("""
            def _shed_main():
                import sys as sys
                exit_code = main()
                if exit_code is not None:
                    sys.exit(exit_code)

            if __name__ == "__main__":
                _shed_main()
        """.trimIndent())
    }
}
