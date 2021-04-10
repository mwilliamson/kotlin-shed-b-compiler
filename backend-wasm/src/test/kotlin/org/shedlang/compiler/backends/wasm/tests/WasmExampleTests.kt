package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.SourceError
import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.backends.wasm.WasmCompiler
import org.shedlang.compiler.backends.wasm.wasm.WasmBinaryFormat
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
        "IntDivision.shed",
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

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.map { testProgram -> DynamicTest.dynamicTest(testProgram.name) {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    val watPath = temporaryDirectory.path.resolve("program.wat")
                    val wasmPath = temporaryDirectory.path.resolve("program.wasm")
                    val moduleSet = testProgram.load()
                    val image = loadModuleSet(moduleSet)

                    val compilationResult = WasmCompiler(image = image, moduleSet = moduleSet).compile(
                        mainModule = testProgram.mainModule
                    )

                    watPath.toFile().writeText(compilationResult.wat)

                    wasmPath.toFile().outputStream().use { outputStream ->
                        WasmBinaryFormat.writeModule(compilationResult.module, outputStream, lateIndices = compilationResult.lateIndices)
                    }

                    val resultWat = executeWasm(watPath, args = testProgram.args)
                    assertThat("stdout was:\n" + resultWat.stdout + "\nstderr was:\n" + resultWat.stderr, resultWat, testProgram.expectedResult)

                    val resultWasm = executeWasm(wasmPath, args = testProgram.args)
                    assertThat("stdout was:\n" + resultWasm.stdout + "\nstderr was:\n" + resultWasm.stderr, resultWasm, testProgram.expectedResult)
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

    private fun executeWasm(path: Path, args: List<String>): ExecutionResult {
        return run(
            listOf("wasmtime", path.toString()) + args,
            workingDirectory = path.parent.toFile(),
        )
    }
}
