package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.SourceError
import org.shedlang.compiler.backends.tests.*
import org.shedlang.compiler.backends.wasm.WasmCompiler
import org.shedlang.compiler.backends.wasm.wasm.WasmBinaryFormat
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.stackir.loadModuleSet
import java.nio.file.Path

class WasmExampleTests {
    private val disabledTests = setOf<String>(
        "cast",
        "ConstantField.shed",
        "Echo.shed",
        "EffectHandlerDiscard.shed",
        "EffectHandlerExitOrResume.shed",
        "FieldDestructuring.shed",
        "HandleWithState.shed",
        "Matchers.shed",
        "nestedStringBuilder",
        "nestedStringBuilderAndNonLocalReturns",
        "NonLocalReturn.shed",
        "NonLocalReturnInline.shed",
        "NonLocalReturnMultipleOperations.shed",
        "NonLocalReturnMultipleValues.shed",
        "NonLocalReturnNested.shed",
        "PolymorphicMap.shed",
        "RecursiveFactorial.shed",
        "RecursiveFibonacci.shed",
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
                        val moduleSet = testProgram.load()
                        val image = loadModuleSet(moduleSet)

                        val compilationResult = WasmCompiler(
                            image = image,
                            moduleSet = moduleSet
                        ).compile(
                            mainModule = testProgram.mainModule,
                        )
                        val objectFilePath = temporaryDirectory.path.resolve("program.o")
                        objectFilePath.toFile().outputStream()
                            .use { outputStream ->
                                WasmBinaryFormat.writeObjectFile(
                                    compilationResult.module,
                                    outputStream,
                                    tagValuesToInt = compilationResult.tagValuesToInt,
                                )
                            }

                        val outputPath = temporaryDirectory.path.resolve("program.wasm")
                        val intToStringObjectPath = findRoot().resolve("backend-wasm/stdlib/Core.IntToString.o")
                        run(listOf("wasm-ld",  objectFilePath.toString(), intToStringObjectPath.toString(), "-o", outputPath.toString())).throwOnError()

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
