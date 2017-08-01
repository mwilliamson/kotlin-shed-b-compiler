package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.ast.ModuleNode
import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Path

fun compile(frontendResult: FrontEndResult, target: Path) {

    frontendResult.modules.forEach({ module ->
        val modulePath = modulePath(module.path)
        val destination = target.resolve(modulePath)
        destination.parent.toFile().mkdirs()
        destination.toFile().writer(StandardCharsets.UTF_8).use { writer ->
            compileModule(
                module = module.node,
                writer = writer
            )
        }
    })
}


private fun modulePath(path: List<String>) = path.joinToString(File.separator) + ".js"

private fun compileModule(module: ModuleNode, writer: Writer) {
    val generateCode = generateCode(module)
    val contents = stdlib + serialise(generateCode) + "\n"
    writer.write(contents)
}

private val stdlib = """
    function intToString(value) {
        return value.toString();
    }

    function print(value) {
        console.log(value);
    }

    function declareShape(name) {
        return {
            name: name,
            typeId: freshTypeId()
        };
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
"""
