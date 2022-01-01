package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.CompilerException
import org.shedlang.compiler.backends.llvm.LlvmCompiler
import org.shedlang.compiler.backends.llvm.LlvmIrBuilder
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.stackir.loadModuleSet
import org.shedlang.compiler.SourceError
import org.shedlang.compiler.backends.withLineNumbers

class LlvmExampleTests {
    private val disabledTests = setOf<String>(
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.map { testProgram -> DynamicTest.dynamicTest(testProgram.name) {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    val outputPath = temporaryDirectory.file.toPath().resolve("program.ll")
                    val moduleSet = testProgram.load()
                    val image = loadModuleSet(moduleSet)

                    val compilationResult = LlvmCompiler(image = image, moduleSet = moduleSet, irBuilder = LlvmIrBuilder()).compile(
                        mainModule = testProgram.mainModule
                    )
                    outputPath.toFile().writeText(compilationResult.llvmIr)

                    val result = executeLlvmIr(outputPath, linkerFiles = compilationResult.linkerFiles, args = testProgram.args)
                    assertThat("stdout was:\n" + result.stdout + "\nstderr was:\n" + result.stderr, result, testProgram.expectedResult)
                }
            } catch (error: CompilerException) {
                print(error.source.describe())
                throw error
            }
        } }
    }
}
