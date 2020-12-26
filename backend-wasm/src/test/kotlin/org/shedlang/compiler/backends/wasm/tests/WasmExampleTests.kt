package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.SourceError
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.backends.wasm.Wasi
import org.shedlang.compiler.backends.wasm.Wat
import org.shedlang.compiler.backends.withLineNumbers
import org.shedlang.compiler.stackir.Image
import org.shedlang.compiler.stackir.loadModuleSet
import java.nio.file.Path

class WasmExampleTests {
    private val disabledTests = setOf<String>(
        "BooleanOperations.shed",
        "cast",
        "Cons.shed",
        "ConstantField.shed",
        "DefaultExitCode.shed",
        "dependencies",
        "Echo.shed",
        "EffectHandlerDiscard.shed",
        "EffectHandlerExitOrResume.shed",
        "ExitCode.shed",
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

                    val compilationResult = Compiler(image = image, moduleSet = moduleSet).compile(
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

internal class Compiler(private val image: Image, private val moduleSet: ModuleSet) {
    class CompilationResult(val wat: String)

    fun compile(mainModule: ModuleName): CompilationResult {
        val messageOffset = 8
        val message = "Hello, world!\n"
        val wat = Wat.module(
            imports = listOf(
                Wasi.importFdWrite("fd_write"),
            ),
            body = listOf(
                Wat.data(offset = messageOffset, value = message),
                Wat.func(
                    identifier = "start",
                    body = listOf(
                        Wat.I.i32Store(Wat.i32Const(0), Wat.i32Const(messageOffset)),
                        Wat.I.i32Store(Wat.i32Const(4), Wat.i32Const(message.length)),
                    ),
                ),
                Wat.start("start"),
                Wat.func(
                    identifier = "main",
                    exportName = "_start",
                    body = listOf(
                        Wasi.callFdWrite(
                            identifier = "fd_write",
                            fileDescriptor = Wasi.stdout,
                            iovs = Wat.i32Const(0),
                            iovsLen = Wat.i32Const(1),
                            nwritten = Wat.i32Const(8 + message.length),
                        ),
                        Wat.I.drop,
                    ),
                ),
            ),
        ).serialise()
        return CompilationResult(wat = wat)
    }
}
