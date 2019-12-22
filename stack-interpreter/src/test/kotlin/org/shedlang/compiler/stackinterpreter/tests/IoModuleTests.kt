package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.IrString

class IoModuleTests {
    private val moduleName = listOf(Identifier("Core"), Identifier("Io"))

    @Test
    fun printBuiltinWritesToStdout() {
        val world = InMemoryWorld()

        callFunction(
            moduleName = moduleName,
            functionName = "print",
            arguments = listOf(
                IrString("hello")
            ),
            world = world
        )

        assertThat(world.stdout, equalTo("hello"))
    }
}
