package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.NodeSource
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.backends.javascript.ast.JavascriptAssignmentNode
import org.shedlang.compiler.backends.javascript.ast.JavascriptExpressionStatementNode
import org.shedlang.compiler.backends.javascript.ast.JavascriptStringLiteralNode
import org.shedlang.compiler.backends.javascript.ast.JavascriptVariableReferenceNode
import org.shedlang.compiler.backends.readResourceText
import org.shedlang.compiler.backends.resourceStream
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path

val backend = object: Backend {
    override fun compile(moduleSet: ModuleSet, mainModule: ModuleName, target: Path) {
        for (module in moduleSet.modules) {
            when (module) {
                is Module.Shed -> {
                    val javascriptModule = compileModule(
                        module = module
                    )
                    writeModule(target, javascriptModule)
                }
                is Module.Native -> {
                    moduleWriter(target, module.name.map(Identifier::value)).use { writer ->
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
        val resourcePrefix = "org/shedlang/compiler/backends/javascript/modules/"
        val resourceName = resourcePrefix + module.name.map { part -> part.value }.joinToString("/") + ".js"
        return resourceStream(resourceName)
    }

    private fun writeModule(target: Path, javascriptModule: JavascriptModule) {
        moduleWriter(target, javascriptModule.name).use { writer ->
            writer.write(javascriptModule.source)
        }
    }

    private fun moduleWriter(target: Path, moduleName: List<String>): OutputStreamWriter {
        val modulePath = modulePath(moduleName)
        val destination = target.resolve(modulePath)
        destination.parent.toFile().mkdirs()
        return destination.toFile().writer(StandardCharsets.UTF_8)
    }

    override fun run(path: Path, module: ModuleName, args: List<String>): Int {
        val modulePath = module.map(Identifier::value).joinToString("/") + ".js"
        val process = ProcessBuilder(listOf("node", modulePath) + args)
            .inheritIO()
            .directory(path.toFile())
            .start()
        return process.waitFor()
    }

    override fun generateBindings(target: Path) {
    }
}

fun compile(frontendResult: ModuleSet, mainModule: ModuleName, target: Path) {
    backend.compile(frontendResult, mainModule = mainModule, target = target)
}

private fun modulePath(path: List<String>) = path.joinToString(File.separator) + ".js"

private fun compileModule(module: Module.Shed): JavascriptModule {
    val generateCode = generateCode(module = module)

    // TODO: remove duplication with import code in codeGenerator
    val builtinsPath = "./" + "../".repeat(module.name.size - 1) + "builtins"
    val builtins = "const \$shed = require(\"$builtinsPath\");\n" + builtinNames.map { builtinName ->
        "const ${builtinName} = \$shed.${builtinName};\n"
    }.joinToString("")
    val main = if (module.hasMain()) {
        """
            if (require.main === module) {
                const EventEmitter = require("events");
                // Create an event emitter to prevent exiting before main is done
                const eventEmitter = new EventEmitter();
                eventEmitter.on("run", async function() {
                    try {
                        const exitCode = await (main.async ? main.async : main)();
                        if (exitCode != null) {
                            process.exit(Number(exitCode));
                        }
                    } catch (error) {
                        console.error(error);
                        process.exit(1);
                    }
                });
                eventEmitter.emit("run")
            }
        """.trimIndent()
    } else {
        ""
    }
    // TODO: test module name
    val moduleName = JavascriptExpressionStatementNode(
        JavascriptAssignmentNode(
            target = JavascriptVariableReferenceNode(
                "moduleName",
                source = NodeSource(module.node)
            ),
            expression = JavascriptStringLiteralNode(
                module.name.map(Identifier::value).joinToString("."),
                source = NodeSource(module.node)
            ),
            source = NodeSource(module.node)
        ),
        source = NodeSource(module.node)
    )
    val contents = builtins +
        serialise(moduleName, indentation = 0) +
        "(function() {\n" + serialise(generateCode) + main + "})();\n"
    return JavascriptModule(
        name = module.name.map(Identifier::value),
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
    "empty"
)
