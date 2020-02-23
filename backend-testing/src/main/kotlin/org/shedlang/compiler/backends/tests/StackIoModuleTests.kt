package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.IrString
import org.shedlang.compiler.types.UnitType

abstract class StackIoModuleTests(private val environment: StackIrExecutionEnvironment) {
    private val moduleName = listOf(Identifier("Core"), Identifier("Io"))

    @Test
    fun printBuiltinWritesToStdout() {
        val result = callFunction(
            environment = environment,
            moduleName = moduleName,
            functionName = "print",
            arguments = listOf(
                IrString("hello")
            ),
            type = UnitType
        )

        assertThat(result.stdout, equalTo("hello"))
    }
}
