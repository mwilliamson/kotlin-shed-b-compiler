package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.*
import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.stackinterpreter.World
import org.shedlang.compiler.stackinterpreter.executeMain
import org.shedlang.compiler.stackir.loadModuleSet

class StackInterpreterExampleTests {
    private val disabledTests = setOf<String>(
        "TailRec.shed"
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.mapNotNull { testProgram ->
            DynamicTest.dynamicTest(testProgram.name) {
                try {
                    val modules = testProgram.load()
                    val image = loadModuleSet(modules)
                    val world = InMemoryWorld(args = testProgram.args)
                    val exitCode = executeMain(
                        mainModule = testProgram.mainModule,
                        image = image,
                        world = world
                    )

                    val executionResult = ExecutionResult(
                        exitCode = exitCode,
                        stderr = "",
                        stdout = world.stdout
                    )
                    assertThat(executionResult, testProgram.expectedResult)
                } catch (error: CompilerError) {
                    print(error.source.describe())
                    throw error
                }
            }
        }
    }
}

class InMemoryWorld(override val args: List<String>) : World {
    private val stdoutBuilder: StringBuilder = StringBuilder()

    override fun writeToStdout(value: String) {
        stdoutBuilder.append(value)
    }

    val stdout: String
        get() = stdoutBuilder.toString()

}
