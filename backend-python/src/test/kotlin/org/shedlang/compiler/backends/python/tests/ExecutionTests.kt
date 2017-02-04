package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.backends.python.serialise
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.read

class ExecutionTests {
    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms.map({ testProgram -> DynamicTest.dynamicTest(testProgram.name, {
            val module = read(filename = "<string>", input = testProgram.source)
            val generateCode = generateCode(module)
            val stdlib = """\
                intToString = str
            """.trimMargin()
            val contents = stdlib + "\n" + serialise(generateCode) + "\nmain()\n"
            val result = run(listOf("python", "-c", contents))
            assertThat(result, equalTo(testProgram.expectedResult))
        }) })
    }
}
