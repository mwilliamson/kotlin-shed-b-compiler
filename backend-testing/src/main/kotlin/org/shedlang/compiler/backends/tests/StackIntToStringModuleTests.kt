package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.IrInt
import org.shedlang.compiler.stackir.IrValue
import org.shedlang.compiler.types.StringType

abstract class StackIntToStringModuleTests(private val environment: StackIrExecutionEnvironment) {
    private val moduleName = listOf(Identifier("Core"), Identifier("IntToString"))

    @Test
    fun zero() {
        val result = intToString(0)

        assertThat(result, isString("0"))
    }

    @Test
    fun smallPositiveInteger() {
        val result = intToString(42)

        assertThat(result, isString("42"))
    }

    @Test
    fun smallNegativeInteger() {
        val result = intToString(-42)

        assertThat(result, isString("-42"))
    }

    private fun intToString(value: Int): IrValue {
        val result = callFunction(
            environment = environment,
            moduleName = moduleName,
            functionName = "intToString",
            arguments = listOf(
                IrInt(value)
            ),
            type = StringType
        )
        return result.value
    }
}
