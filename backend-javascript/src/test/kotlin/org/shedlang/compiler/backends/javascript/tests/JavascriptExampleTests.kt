package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.javascript.compile
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.SourceError

class JavascriptExampleTests {
    private val disabledTests = setOf<String>(
        "EffectHandlerDiscard.shed",
        "EffectHandlerExitOrResume.shed",
        "HandleWithState.shed",
        "IntDivision.shed",
        "nestedStringBuilderAndNonLocalReturns",
        "NonLocalReturnNested.shed",
        "Resume.shed",
        "ResumeWithValue.shed",
        "ShapeSplat.shed",
        "TailRec.shed",
        "stdlib",
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.map({ testProgram -> DynamicTest.dynamicTest(testProgram.name, {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    compile(testProgram.load(), mainModule = testProgram.mainModule, target = temporaryDirectory.file.toPath())
                    val mainJsModule = "./" + testProgram.mainModule.map(Identifier::value).joinToString("/") + ".js"
                    val result = run(
                        listOf("node", mainJsModule) + testProgram.args,
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
        }) })
    }
}
