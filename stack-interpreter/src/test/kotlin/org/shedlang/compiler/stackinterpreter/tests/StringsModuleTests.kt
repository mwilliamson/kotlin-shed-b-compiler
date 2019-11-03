package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackinterpreter.*

class StringsModuleTests {
    private val moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings"))

    @Test
    fun codePointAt_whenStringIsBeforeEndOfStringThenCodePointIsReturned() {
        val value = call("codePointAt", listOf(InterpreterInt(4.toBigInteger()), InterpreterString("hello")))

        val codePoint = (value as InterpreterShapeValue).field(Identifier("value"))
        assertThat(codePoint, isCodePoint('o'))
    }

    @Test
    fun codePointAt_whenIndexIsAfterEndOfStringThenNoneIsReturned() {
        val value = call("codePointAt", listOf(InterpreterInt(5.toBigInteger()), InterpreterString("hello")))

        assertThat(
            { (value as InterpreterShapeValue).field(Identifier("value")) },
            throws<Exception>()
        )
    }

    @Test
    fun codePointCount() {
        val value = call("codePointCount", listOf(InterpreterString("hello")))

        assertThat(value, isInt(5))
    }

    @Test
    fun codePointToHexString() {
        val value = call("codePointToHexString", listOf(InterpreterCodePoint(42)))

        assertThat(value, isString("2A"))
    }

    @Test
    fun codePointToInt() {
        val value = call("codePointToInt", listOf(InterpreterCodePoint(42)))

        assertThat(value, isInt(42))
    }

    @Test
    fun codePointToString() {
        val value = call("codePointToString", listOf(InterpreterCodePoint(42)))

        assertThat(value, isString("*"))
    }

    @Test
    fun replace() {
        val value = call("replace", listOf(
            InterpreterString("bc"),
            InterpreterString("d"),
            InterpreterString("abc abc")
        ))

        assertThat(value, isString("ad ad"))
    }

    @Test
    fun substring() {
        val value = call("substring", listOf(
            InterpreterInt(2.toBigInteger()),
            InterpreterInt(4.toBigInteger()),
            InterpreterString("hello")
        ))

        assertThat(value, isString("ll"))
    }

    private fun call(functionName: String, arguments: List<InterpreterValue>): InterpreterValue {
        return callFunction(
            moduleName = moduleName,
            functionName = functionName,
            arguments = arguments
        )
    }
}
