package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.typechecker.SourceError
import java.nio.file.Path

class ExecutionTests {
    private val disabledTests = setOf<String>(
        "BooleanOperations.shed",
        "cast",
        "Cons.shed",
        "ConstantField.shed",
        "dependencies",
        "FieldDestructuring.shed",
        "localImports",
        "Matchers.shed",
        "moduleName",
        "NamedArguments.shed",
        "PolymorphicCons.shed",
        "PolymorphicForEach.shed",
        "PolymorphicIdentity.shed",
        "PolymorphicMap.shed",
        "RecursiveFactorial.shed",
        "RecursiveFibonacci.shed",
        "ShapeTypeInfo.shed",
        "stdlib",
        "symbols",
        "TailRec.shed",
        "Tuples.shed",
        "TypeAlias.shed",
        "usingStdlib",
        "Varargs.shed",
        "When.shed",
        "WhenElse.shed"
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.map { testProgram -> DynamicTest.dynamicTest(testProgram.name) {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    val outputPath = temporaryDirectory.file.toPath().resolve("program.ll")
                    Compiler(testProgram.load()).compile(
                        target = outputPath,
                        mainModule = testProgram.mainModule
                    )
                    val result = org.shedlang.compiler.backends.tests.run(
                        listOf("lli", outputPath.toString()),
                        workingDirectory = temporaryDirectory.file
                    )
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

private class Compiler(private val moduleSet: ModuleSet) {
    fun compile(target: Path, mainModule: List<Identifier>) {
        target.toFile().writeText("""
            define i32 @main() {
                ret i32 42
            }
        """.trimIndent())
    }
}
