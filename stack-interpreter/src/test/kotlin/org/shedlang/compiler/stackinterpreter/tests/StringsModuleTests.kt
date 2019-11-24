package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.tests.isSequence

class StringsModuleTests {
    private val moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings"))

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
    fun indexAtCodePointCount_whenCharactersAreInBmpThenIndexIsCount() {
        val value = call("indexAtCodePointCount", listOf(InterpreterInt(1.toBigInteger()), InterpreterString("abc")))

        assertThat(value, isInt(1))
    }

    @Test
    fun indexAtCodePointCount_whenCharactersAreNotInBmpThenIndexIsGreaterThanCount() {
        val value = call("indexAtCodePointCount", listOf(InterpreterInt(1.toBigInteger()), InterpreterString("\uD835\uDD3Cbc")))

        assertThat(value, isInt(2))
    }

    @Test
    fun lastIndexIsLengthOfString() {
        val value = call("lastIndex", listOf(InterpreterString("\uD835\uDD3Cbc")))

        assertThat(value, isInt(4))
    }

    @Test
    fun next_whenStringIsBeforeEndOfStringThenCodePointIsReturned() {
        val value = call("next", listOf(InterpreterInt(4.toBigInteger()), InterpreterString("hello")))

        val tuple = (value as InterpreterShapeValue).field(Identifier("value"))
        assertThat(tuple, isTuple(isSequence(
            isCodePoint('o'),
            isInt(5)
        )))
    }

    @Test
    fun next_whenIndexIsAfterEndOfStringThenNoneIsReturned() {
        val value = call("next", listOf(InterpreterInt(5.toBigInteger()), InterpreterString("hello")))

        assertThat(
            { (value as InterpreterShapeValue).field(Identifier("value")) },
            throws<Exception>()
        )
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
