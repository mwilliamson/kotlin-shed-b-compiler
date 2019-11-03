package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackinterpreter.InterpreterInt

class IntToStringModuleTests {
    private val moduleName = listOf(Identifier("Core"), Identifier("IntToString"))

    @Test
    fun printBuiltinWritesToStdout() {
        val world = InMemoryWorld()

        val value = callFunction(
            moduleName = moduleName,
            functionName = "intToString",
            arguments = listOf(
                InterpreterInt(42.toBigInteger())
            ),
            world = world
        )

        assertThat(value, isString("42"))
    }
}
