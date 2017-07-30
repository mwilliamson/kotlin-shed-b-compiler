package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.python.compile
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.identifyModule
import org.shedlang.compiler.read
import org.shedlang.compiler.typechecker.TypeCheckError

class ExecutionTests {
    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().map({ testProgram -> DynamicTest.dynamicTest(testProgram.name, {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    val frontendResult = read(
                        base = testProgram.base,
                        path = testProgram.main
                    )
                    compile(frontendResult, target = temporaryDirectory.file.toPath())
                    val mainModule = identifyModule(testProgram.main).joinToString(".")
                    val result = run(
                        listOf("python", "-m", mainModule),
                        workingDirectory = temporaryDirectory.file
                    )
                    assertThat(result, equalTo(testProgram.expectedResult))
                }
            } catch (error: TypeCheckError) {
                print(error.source.describe())
                throw error
            }
        }) })
    }
}
