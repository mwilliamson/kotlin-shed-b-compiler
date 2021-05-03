package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.SourceError
import org.shedlang.compiler.backends.tests.*
import org.shedlang.compiler.backends.wasm.WasmBackend
import java.nio.file.Path

class WasmExampleTests {
    private val disabledTests = setOf<String>(
        "ConstantField.shed",
        "Echo.shed",
        "EffectHandlerDiscard.shed",
        "EffectHandlerExitOrResume.shed",
        "HandleWithState.shed",
        "Matchers.shed",
        "nestedStringBuilder",
        "nestedStringBuilderAndNonLocalReturns",
        "NonLocalReturn.shed",
        "NonLocalReturnInline.shed",
        "NonLocalReturnMultipleOperations.shed",
        "NonLocalReturnMultipleValues.shed",
        "NonLocalReturnNested.shed",
        "Resume.shed",
        "ResumeWithValue.shed",
        "ShapeInFunction.shed",
        "ShapeTypeInfo.shed",
        "stdlib",
        "stringBuilder",
        "TailRec.shed",
        "TypeName.shed",
        "usingStdlib",
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
                        assertThat(
                            "stdout was:\n" + result.stdout + "\nstderr was:\n" + result.stderr,
                            result,
                            testProgram.expectedResult
                        )
                    }
                } catch (error: SourceError) {
                    print(error.source.describe())
                    throw error
                } catch (error: CompilerError) {
                    print(error.source.describe())
                    throw error
                }
            }
        }
    }

    private fun executeWasm(path: Path, args: List<String>): ExecutionResult {
        return run(
            listOf("wasmtime", path.toString()) + args,
            workingDirectory = path.parent.toFile(),
        )
    }
}
