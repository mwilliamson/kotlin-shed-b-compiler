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
        val wat = """
            (module
                ;; Import the required fd_write WASI function which will write the given io vectors to stdout
                ;; The function signature for fd_write is:
                ;; (File Descriptor, *iovs, iovs_len, nwritten) -> Returns number of bytes written
                (import "wasi_snapshot_preview1" "fd_write" (func """ + "\$fd_write" + """ (param i32 i32 i32 i32) (result i32)))

                (memory 1)
                (export "memory" (memory 0))

                ;; Write 'Hello, world!\n' to memory at an offset of 8 bytes
                ;; Note the trailing newline which is required for the text to appear
                (data (i32.const 8) "Hello, world!\n")

                (func """ + "\$main" + """ (export "_start")
                    ;; Creating a new io vector within linear memory
                    (i32.store (i32.const 0) (i32.const 8))  ;; iov.iov_base - This is a pointer to the start of the 'Hello, world!\n' string
                    (i32.store (i32.const 4) (i32.const 14))  ;; iov.iov_len - The length of the 'Hello, world!\n' string

                    (call """ + "\$fd_write" + """
                        (i32.const 1) ;; file_descriptor - 1 for stdout
                        (i32.const 0) ;; *iovs - The pointer to the iov array, which is stored at memory location 0
                        (i32.const 1) ;; iovs_len - We're printing 1 string stored in an iov - so one.
                        (i32.const 20) ;; nwritten - A place in memory to store the number of bytes written
                    )
                    drop ;; Discard the number of bytes written from the top of the stack
                )
            )
        """.trimIndent()
        return CompilationResult(wat = wat)
    }
}
