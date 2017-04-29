package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.backends.python.serialise
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.read
import org.shedlang.compiler.typechecker.ResolvedReferences
import org.shedlang.compiler.typechecker.TypeCheckError
import java.io.File
import java.io.Writer
import java.nio.charset.StandardCharsets

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
                    val module = frontendResult.modules.single()
                    val moduleName = module.path
                    val modulePath = moduleName.joinToString(File.separator) + ".py"
                    val destination = temporaryDirectory.file.resolve(modulePath)
                    destination.writer(StandardCharsets.UTF_8).use { writer ->
                        compileModule(
                            module = module.node,
                            references = module.references,
                            writer = writer
                        )
                    }
                    val result = run(
                        listOf("python", "-m", moduleName.joinToString(".")),
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

    private fun compileModule(
        module: ModuleNode,
        references: ResolvedReferences,
        writer: Writer
    ) {
        val generateCode = generateCode(module, references)
        val stdlib = """\
            int_to_string = str
        """.trimMargin()
        val contents = stdlib + "\n" + serialise(generateCode) + "\nmain()\n"
        writer.write(contents)
    }
}
