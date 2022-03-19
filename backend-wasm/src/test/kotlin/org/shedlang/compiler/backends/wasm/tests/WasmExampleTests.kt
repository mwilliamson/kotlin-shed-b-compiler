package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.CompilerException
import org.shedlang.compiler.SourceError
import org.shedlang.compiler.backends.tests.*
import org.shedlang.compiler.backends.wasm.WasmBackend
import org.shedlang.compiler.backends.wasm.generateWasmCommand
import java.lang.AssertionError
import java.nio.file.Path

class WasmExampleTests {
    private val disabledTests = setOf<String>(
        "HandleWithState.shed",
        "stdlib",
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        val testPrograms = testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }
        return testPrograms.map { testProgram ->
            DynamicTest.dynamicTest(testProgram.name) {
                try {
                    temporaryDirectory().use { temporaryDirectory ->
                        val outputPath = temporaryDirectory.path.resolve("program.wasm")
                        WasmBackend.compile(
                            moduleSet = testProgram.load(),
                            mainModule = testProgram.mainModule,
                            target = outputPath
                        )

                        val result = executeWasm(outputPath, args = testProgram.args)
                        try {
                            assertThat(
                                "stdout was:\n" + result.stdout + "\nstderr was:\n" + result.stderr,
                                result,
                                testProgram.expectedResult
                            )
                        } catch (error: AssertionError) {
//                            print(objdump(outputPath))
                            throw error
                        }
                    }
                } catch (error: CompilerException) {
                    print(error.source.describe())
                    throw error
                }
            }
        }
    }

    private fun executeWasm(path: Path, args: List<String>): ExecutionResult {
        return run(
            generateWasmCommand(path, args),
            workingDirectory = path.parent.toFile(),
        )
    }

    private fun objdump(path: Path): String {
        return run(listOf("wasm-objdump", "-dx", path.toString())).throwOnError().stdout
    }
}
