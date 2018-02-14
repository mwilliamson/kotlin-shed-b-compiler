package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.Module
import org.shedlang.compiler.backends.Backend
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

        writeModule(target, builtinModule)
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

private val builtinModule = JavascriptModule(
    name = listOf("builtins"),
    source = """
        function intToString(value) {
            return value.toString();
        }

        function print(value) {
            process.stdout.write(value);
        }

        function list() {
            return Array.prototype.slice.call(arguments);
        }

        function all(list) {
            for (var index = 0; index < list.length; index++) {
                if (!list[index]) {
                    return false;
                }
            }
            return true;
        }

        function map(func, list) {
            return list.map(func);
        }

        function forEach(func, list) {
            return list.forEach(func);
        }

        function declareShape(name) {
            const typeId = freshTypeId();

            function shape(fields) {
                if (fields === undefined) {
                    fields = {};
                }
                fields.${"$"}shedType = shape;
                return fields;
            }

            shape.typeId = typeId;

            return shape;
        }

        var nextTypeId = 1;
        function freshTypeId() {
            return nextTypeId++;
        }

        function isType(value, type) {
            return value != null && value.${"$"}shedType === type;
        }

        function partial(receiver, positional, named) {
            // TODO: doesn't work if two sets of named arguments are passed
            // (e.g. (x ~ (y = 1))(z = 2)
            // Probably need to generate the function at code-gen, when we can
            // use the type of the function to work out whether we expect more
            // named arguments
            return function() {
                var args = positional.slice();
                for (var argumentIndex = 0; argumentIndex < arguments.length; argumentIndex++) {
                    args.push(arguments[argumentIndex]);
                }
                args.push(named);
                return receiver.apply(null, args);
            };
        }

        module.exports = {
            declareShape: declareShape,
            isType: isType,
            partial: partial,

            all: all,
            forEach: forEach,
            intToString: intToString,
            list: list,
            map: map,
            print: print,
        };
    """.trimIndent()
)

val builtinNames = listOf(
    "all",
    "forEach",
    "intToString",
    "list",
    "map",
    "print"
);
