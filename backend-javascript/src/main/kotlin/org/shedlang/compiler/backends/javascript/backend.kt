package org.shedlang.compiler.backends.javascript

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
            val modulePath = modulePath(module.path)
            val destination = target.resolve(modulePath)
            destination.parent.toFile().mkdirs()
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

private fun modulePath(path: List<String>) = path.joinToString(File.separator) + ".js"

private fun compileModule(module: Module, writer: Writer) {
    val generateCode = generateCode(module.node)
    val contents = stdlib + serialise(generateCode) + "\n"
    writer.write(contents)
    if (module.hasMain()) {
        writer.write("""
            if (require.main === module) {
                (function() {
                    const exitCode = main();
                    if (exitCode != null) {
                        process.exit(exitCode);
                    }
                })();
            }
        """.trimIndent())
    }
}

private val stdlib = """
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

    var ${"$"}shed = {
        declareShape: declareShape,
        isType: isType
    };
""".trimIndent()
