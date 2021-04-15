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
        "Tuples.shed",
        "TypeName.shed",
        "usingStdlib",
    )

    private interface CompilationMethod {
        val name: String
        val isObjectFile: Boolean
        fun compile(compilationResult: WasmCompiler.CompilationResult, temporaryDirectory: TemporaryDirectory): Path
    }

    private val compilationMethods = listOf<CompilationMethod>(
        object : CompilationMethod {
            override val name: String
                get() = "binary format object file"
            override val isObjectFile: Boolean
                get() = true

            override fun compile(
                compilationResult: WasmCompiler.CompilationResult,
                temporaryDirectory: TemporaryDirectory
            ): Path {
                val objectFilePath = temporaryDirectory.path.resolve("program.o")
                objectFilePath.toFile().outputStream()
                    .use { outputStream ->
                        WasmBinaryFormat.writeObjectFile(
                            compilationResult.module,
                            outputStream,
                            lateIndices = compilationResult.lateIndices,
                        )
                    }

                val wasmPath = temporaryDirectory.path.resolve("program.wasm")
                val intToStringObjectPath = findRoot().resolve("backend-wasm/stdlib/Core.IntToString.o")
                run(listOf("wasm-ld",  objectFilePath.toString(), intToStringObjectPath.toString(), "-o", wasmPath.toString())).throwOnError()
                println(run(listOf("wasm-objdump", "-xd", wasmPath.toString())).throwOnError())

                return wasmPath
            }
        }
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        val testPrograms = testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }
        return compilationMethods.flatMap { compilationMethod ->
            testPrograms.map { testProgram ->
                DynamicTest.dynamicTest("${compilationMethod.name}: ${testProgram.name}") {
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

                            val outputPath = compilationMethod.compile(compilationResult, temporaryDirectory)

                            val result =
                                executeWasm(outputPath, args = testProgram.args)
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
    }

    private fun executeWasm(path: Path, args: List<String>): ExecutionResult {
        return run(
            listOf("wasmtime", path.toString()) + args,
            workingDirectory = path.parent.toFile(),
        )
    }
}
