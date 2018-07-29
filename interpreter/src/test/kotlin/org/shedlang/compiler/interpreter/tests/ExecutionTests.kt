package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.interpreter.fullyEvaluate
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.typechecker.SourceError

class ExecutionTests {
    private val blacklist = listOf("stdlib")

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().mapNotNull { testProgram ->
            if (blacklist.contains(testProgram.name)) {
                null
            } else {
                DynamicTest.dynamicTest(testProgram.name, {
                    try {
                        val modules = testProgram.load()
                        val result = fullyEvaluate(modules, testProgram.mainModule)

                        val executionResult = ExecutionResult(
                            exitCode = result.exitCode,
                            stderr = "",
                            stdout = result.stdout
                        )
                        assertThat(executionResult, testProgram.expectedResult)
                    } catch (error: SourceError) {
                        print(error.source.describe())
                        throw error
                    } catch (error: CompilerError) {
                        print(error.source.describe())
                        throw error
                    }
                })
            }
        }
    }
}
