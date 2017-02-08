package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.backends.javascript.generateCode
import org.shedlang.compiler.backends.javascript.serialise
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.read
import org.shedlang.compiler.typechecker.TypeCheckError

class ExecutionTests {
    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms.map({ testProgram -> DynamicTest.dynamicTest(testProgram.name, {
            try {
                val frontEndResult = read(filename = "<string>", input = testProgram.source)
                val generateCode = generateCode(frontEndResult.module)
                val stdlib = """
                    function intToString(value) {
                        return value.toString();
                    }

                    function print(value) {
                        console.log(value);
                    }
                """
                val contents = serialise(generateCode) + "\nmain()\n" + stdlib + "\n"
                val result = run(listOf("node", "-e", contents))
                assertThat(result, equalTo(testProgram.expectedResult))
            } catch (error: TypeCheckError) {
                print(error.source.describe())
                throw error
            }
        }) })
    }
}
