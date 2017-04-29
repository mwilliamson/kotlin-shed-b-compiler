package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.backends.javascript.generateCode
import org.shedlang.compiler.backends.javascript.serialise
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.identifyModule
import org.shedlang.compiler.read
import org.shedlang.compiler.typechecker.TypeCheckError
import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets

val stdlib = """
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

class ExecutionTests {
    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().map({ testProgram -> DynamicTest.dynamicTest(testProgram.name, {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    val frontendResult = read(
                        base = testProgram.base,
                        path = testProgram.main
                    )

                    frontendResult.modules.forEach({ module ->
                        val modulePath = modulePath(module.path)
                        val destination = temporaryDirectory.file.resolve(modulePath)
                        destination.toPath().parent.toFile().mkdirs()
                        destination.writer(StandardCharsets.UTF_8).use { writer ->
                            compileModule(
                                module = module.node,
                                writer = writer
                            )
                        }
                    })
                    val mainJsModule = "./" + identifyModule(testProgram.main).joinToString("/")
                    val result = run(
                        listOf("node", "-e", "require(\"${mainJsModule}\").main()"),
                        workingDirectory = temporaryDirectory.file
                    )
                    assertThat(result, equalTo(testProgram.expectedResult))
                }
            } catch (error: TypeCheckError) {
                print(error.source.describe())
                throw error
            }
        }) })
    }

    private fun modulePath(path: List<String>) = path.joinToString(File.separator) + ".js"

    private fun compileModule(module: ModuleNode, writer: Writer) {
        val generateCode = generateCode(module)
        val contents = stdlib + serialise(generateCode) + "\n"
        writer.write(contents)
    }
}
