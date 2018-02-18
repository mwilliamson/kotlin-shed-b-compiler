package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.Module
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.backends.readResourceText
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path

val backend = object: Backend {
    override fun compile(frontEndResult: FrontEndResult, target: Path) {
        frontEndResult.modules.forEach({ module ->
            val javascriptModule = compileModule(
                module = module,
                modules = frontEndResult
            )
            writeModule(target, javascriptModule)
        })

        writeModule(target, builtinModule())
    }

    private fun writeModule(target: Path, javascriptModule: JavascriptModule) {
        val modulePath = modulePath(javascriptModule.name)
        val destination = target.resolve(modulePath)
        destination.parent.toFile().mkdirs()
        destination.toFile().writer(StandardCharsets.UTF_8).use { writer ->
            writer.write(javascriptModule.source)
        }
    }

    override fun run(path: Path, module: List<String>): Int {
        val process = ProcessBuilder("node", module.joinToString("/") + ".js")
            .inheritIO()
            .directory(path.toFile())
            .start()
        return process.waitFor()
    }
}

fun compile(frontendResult: FrontEndResult, target: Path) {
    backend.compile(frontendResult, target = target)
}

private fun modulePath(path: List<String>) = path.joinToString(File.separator) + ".js"

private fun compileModule(module: Module, modules: FrontEndResult): JavascriptModule {
    val generateCode = generateCode(module = module, modules = modules)

    // TODO: remove duplication with import code in codeGenerator
    val builtinsPath = "./" + "../".repeat(module.name.size - 1) + "builtins"
    val builtins = "const \$shed = require(\"$builtinsPath\");\n" + builtinNames.map { builtinName ->
        "const ${builtinName} = \$shed.${builtinName};\n"
    }.joinToString("")
    val main = if (module.hasMain()) {
        """
            if (require.main === module) {
                (function() {
                    const exitCode = main();
                    if (exitCode != null) {
                        process.exit(exitCode);
                    }
                })();
            }
        """.trimIndent()
    } else {
        ""
    }
    val contents = builtins + "(function() {\n" + serialise(generateCode) + main + "})();\n"
    return JavascriptModule(
        name = module.name,
        source = contents
    )
}

private class JavascriptModule(val name: List<String>, val source: String)

private fun builtinModule(): JavascriptModule {
    val contents = readResourceText("org/shedlang/compiler/backends/javascript/modules/builtins.js")
    return JavascriptModule(
        name = listOf("builtins"),
        source = contents
    )
}

val builtinNames = listOf(
    "all",
    "forEach",
    "intToString",
    "list",
    "map",
    "print",
    "reduce"
);
