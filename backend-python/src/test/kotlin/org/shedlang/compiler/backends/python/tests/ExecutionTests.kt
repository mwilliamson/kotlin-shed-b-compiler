package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.python.compile
import org.shedlang.compiler.backends.python.topLevelPythonPackageName
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.typechecker.SourceError

class ExecutionTests {
    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().map({ testProgram -> DynamicTest.dynamicTest(testProgram.name, {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    compile(testProgram.frontEndResult, target = temporaryDirectory.file.toPath())
                    val result = run(
                        listOf("python", "-m", topLevelPythonPackageName + ".main"),
                        workingDirectory = temporaryDirectory.file
                    )
                    assertThat(result, equalTo(testProgram.expectedResult))
                }
            } catch (error: SourceError) {
                print(error.source.describe())
                throw error
            }
        }) })
    }
}
