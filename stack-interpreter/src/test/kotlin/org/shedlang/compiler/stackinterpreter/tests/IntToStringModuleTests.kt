package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.IrInt

class IntToStringModuleTests {
    private val moduleName = listOf(Identifier("Core"), Identifier("IntToString"))

    @Test
    fun smallPositiveInteger() {
        val result = callFunction(
            moduleName = moduleName,
            functionName = "intToString",
            arguments = listOf(
                IrInt(42.toBigInteger())
            )
        )

        assertThat(result.value, isString("42"))
    }
}
