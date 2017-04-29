package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.Module
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.backends.python.serialise
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.identifyModule
import org.shedlang.compiler.read
import org.shedlang.compiler.typechecker.TypeCheckError
import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Path

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
                        val moduleName = module.path
                        val modulePath = moduleName.joinToString(File.separator) + ".py"
                        val destination = temporaryDirectory.file.resolve(modulePath)
                        val pythonPackage = destination.toPath().parent
                        pythonPackage.toFile().mkdirs()
                        addInitFiles(temporaryDirectory.file.toPath(), pythonPackage)

                        destination.writer(StandardCharsets.UTF_8).use { writer ->
                            compileModule(
                                module = module,
                                writer = writer
                            )
                        }
                    })
                    val mainModule = identifyModule(testProgram.main).joinToString(".")
                    val result = run(
                        listOf("python", "-c", "from ${mainModule} import main; main()"),
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

    private fun addInitFiles(base: Path, pythonPackage: Path) {
        var currentPackage = pythonPackage
        while (base != currentPackage) {
            currentPackage.resolve("__init__.py").toFile().createNewFile()
            currentPackage = pythonPackage.parent
        }
    }

    private fun compileModule(module: Module, writer: Writer) {
        val generateCode = generateCode(module.node, module.references)
        val stdlib = """\
            int_to_string = str
        """.trimMargin()
        val contents = stdlib + "\n" + serialise(generateCode) + "\n"
        writer.write(contents)
    }
}
