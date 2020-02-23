package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.IrInt
import org.shedlang.compiler.types.StringType

abstract class StackIntToStringModuleTests(private val environment: StackIrExecutionEnvironment) {
    private val moduleName = listOf(Identifier("Core"), Identifier("IntToString"))

    @Test
    fun smallPositiveInteger() {
        val result = callFunction(
            environment = environment,
            moduleName = moduleName,
            functionName = "intToString",
            arguments = listOf(
                IrInt(42)
            ),
            type = StringType
        )

        assertThat(result.value, isString("42"))
    }
}
