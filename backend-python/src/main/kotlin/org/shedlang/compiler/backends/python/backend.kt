package org.shedlang.compiler.backends.python

import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.Module
import org.shedlang.compiler.backends.Backend
import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Path

val backend = object: Backend {
    override fun compile(frontEndResult: FrontEndResult, target: Path) {
        frontEndResult.modules.forEach({ module ->
            val moduleName = module.path
            val modulePath = moduleName.joinToString(File.separator) + ".py"
            val destination = target.resolve(modulePath)
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
}

fun compile(frontendResult: FrontEndResult, target: Path) {
    backend.compile(frontendResult, target = target)
}

private fun addInitFiles(base: Path, pythonPackage: Path) {
    var currentPackage = pythonPackage
    while (base != currentPackage) {
        currentPackage.resolve("__init__.py").toFile().createNewFile()
        currentPackage = pythonPackage.parent
    }
}

private fun compileModule(module: Module, writer: Writer) {
    val generateCode = generateCode(module.node, module.references)
    val stdlib = """
        int_to_string = str
    """.trimIndent()
    val contents = stdlib + "\n" + serialise(generateCode) + "\n"
    writer.write(contents)
    if (module.hasMain()) {
        writer.write("""
            if __name__ == "__main__":
                main()
        """.trimIndent())
    }
}
