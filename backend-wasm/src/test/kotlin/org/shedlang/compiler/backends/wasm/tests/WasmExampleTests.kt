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
import org.shedlang.compiler.backends.withLineNumbers
import org.shedlang.compiler.stackir.loadModuleSet
import java.nio.file.Path

class WasmExampleTests {
    private val disabledTests = setOf<String>(
        "cast",
        "Cons.shed",
        "ConstantField.shed",
        "dependencies",
        "Echo.shed",
        "EffectHandlerDiscard.shed",
        "EffectHandlerExitOrResume.shed",
        "FieldDestructuring.shed",
        "HandleWithState.shed",
        "IgnoreTarget.shed",
        "IntOperations.shed",
        "localImports",
        "Matchers.shed",
        "moduleName",
        "NamedArguments.shed",
        "nestedStringBuilder",
        "nestedStringBuilderAndNonLocalReturns",
        "NonLocalReturn.shed",
        "NonLocalReturnInline.shed",
        "NonLocalReturnMultipleOperations.shed",
        "NonLocalReturnMultipleValues.shed",
        "NonLocalReturnNested.shed",
        "PolymorphicCons.shed",
        "PolymorphicForEach.shed",
        "PolymorphicIdentity.shed",
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
        "TypeAlias.shed",
        "TypeName.shed",
        "usingStdlib",
        "Varargs.shed",
        "When.shed",
        "WhenDestructuring.shed",
        "WhenElse.shed",
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.map { testProgram -> DynamicTest.dynamicTest(testProgram.name) {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    val outputPath = temporaryDirectory.file.toPath().resolve("program.wat")
                    val moduleSet = testProgram.load()
                    val image = loadModuleSet(moduleSet)

                    val compilationResult = WasmCompiler(image = image, moduleSet = moduleSet).compile(
                        mainModule = testProgram.mainModule
                    )
                    println(withLineNumbers(compilationResult.wat))
                    outputPath.toFile().writeText(compilationResult.wat)

                    val result = executeWat(outputPath, args = testProgram.args)
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

    private fun executeWat(path: Path, args: List<String>): ExecutionResult {
        return run(
            listOf("wasmtime", path.toString()) + args,
            workingDirectory = path.parent.toFile(),
        )
    }
}
