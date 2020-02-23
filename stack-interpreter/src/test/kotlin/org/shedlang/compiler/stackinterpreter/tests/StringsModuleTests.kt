package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackinterpreter.InterpreterValue
import org.shedlang.compiler.stackir.IrInt
import org.shedlang.compiler.stackir.IrString
import org.shedlang.compiler.stackir.IrUnicodeScalar
import org.shedlang.compiler.stackir.IrValue

class StringsModuleTests {
    private val moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings"))

    @Test
    fun unicodeScalarCount() {
        val value = call("unicodeScalarCount", listOf(IrString("hello")))

        assertThat(value, isInt(5))
    }

    @Test
    fun unicodeScalarToHexString() {
        val value = call("unicodeScalarToHexString", listOf(IrUnicodeScalar(42)))

        assertThat(value, isString("2A"))
    }

    @Test
    fun unicodeScalarToInt() {
        val value = call("unicodeScalarToInt", listOf(IrUnicodeScalar(42)))

        assertThat(value, isInt(42))
    }

    @Test
    fun unicodeScalarToString() {
        val value = call("unicodeScalarToString", listOf(IrUnicodeScalar(42)))

        assertThat(value, isString("*"))
    }

    @Test
    fun replace() {
        val value = call("replace", listOf(
            IrString("bc"),
            IrString("d"),
            IrString("abc abc")
        ))

        assertThat(value, isString("ad ad"))
    }

    @Test
    fun substring() {
        val value = call("substring", listOf(
            IrInt(2.toBigInteger()),
            IrInt(4.toBigInteger()),
            IrString("hello")
        ))

        assertThat(value, isString("ll"))
    }

    private fun call(functionName: String, arguments: List<IrValue>): InterpreterValue {
        return callFunction(
            moduleName = moduleName,
            functionName = functionName,
            arguments = arguments
        ).value
    }
}
