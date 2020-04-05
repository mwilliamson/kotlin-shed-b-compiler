package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.IrInt
import org.shedlang.compiler.stackir.IrString
import org.shedlang.compiler.stackir.IrUnicodeScalar
import org.shedlang.compiler.stackir.IrValue
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.StringType
import org.shedlang.compiler.types.Type

abstract class StackStringsModuleTests(private val environment: StackIrExecutionEnvironment) {
    private val moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings"))

    @Test
    fun unicodeScalarCount() {
        val value = call("unicodeScalarCount", listOf(IrString("hello")), type = IntType)

        assertThat(value, isInt(5))
    }

    @Test
    fun unicodeScalarToHexString() {
        val value = call("unicodeScalarToHexString", listOf(IrUnicodeScalar(42)), type = StringType)

        assertThat(value, isString("2A"))
    }

    @Test
    fun unicodeScalarToInt() {
        val value = call("unicodeScalarToInt", listOf(IrUnicodeScalar(42)), type = IntType)

        assertThat(value, isInt(42))
    }

    @Test
    fun unicodeScalarToString() {
        val value = call("unicodeScalarToString", listOf(IrUnicodeScalar(42)), type = StringType)

        assertThat(value, isString("*"))
    }

    @Test
    fun replace() {
        val value = call("replace", listOf(
            IrString("bc"),
            IrString("d"),
            IrString("abc abce")
        ), type = StringType)

        assertThat(value, isString("ad ade"))
    }

    @Test
    fun substring() {
        val value = call("substring", listOf(
            IrInt(2.toBigInteger()),
            IrInt(4.toBigInteger()),
            IrString("hello")
        ), type = StringType)

        assertThat(value, isString("ll"))
    }

    private fun call(functionName: String, arguments: List<IrValue>, type: Type): IrValue {
        return callFunction(
            environment = environment,
            moduleName = moduleName,
            functionName = functionName,
            arguments = arguments,
            type = type
        ).value
    }
}
