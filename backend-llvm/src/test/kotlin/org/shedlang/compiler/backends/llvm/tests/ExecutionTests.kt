package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.llvm.Compiler
import org.shedlang.compiler.backends.llvm.LlvmIrBuilder
import org.shedlang.compiler.backends.llvm.withLineNumbers
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.stackir.loadModuleSet
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.typechecker.SourceError

class ExecutionTests {
    private val disabledTests = setOf<String>(
        "ConstantField.shed",
        "stdlib",
        "symbols",
        "TailRec.shed"
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

                    val llSource = Compiler(image = image, moduleSet = moduleSet, irBuilder = LlvmIrBuilder()).compile(
                        mainModule = testProgram.mainModule
                    )
                    println(withLineNumbers(llSource))
                    outputPath.toFile().writeText(llSource)

                    val includeStrings = moduleSet.module(listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings"))) != null
                    val result = executeLlvmInterpreter(outputPath, includeStrings = includeStrings)
                    assertThat("stdout was:\n" + result.stdout + "\nstderr was:\n" + result.stderr, result, testProgram.expectedResult)
                }
            } catch (error: SourceError) {
                print(error.source.describe())
                throw error
            } catch (error: CompilerError) {
                print(error.source.describe())
                throw error
            }
        } }
    }
}
