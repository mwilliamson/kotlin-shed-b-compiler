package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.wasm.*

class WatTests {
    @Test
    fun moduleHasMemoryDeclarations() {
        val module = Wat.module()

        assertThat(module, equalTo(
            S.list(
                S.string("module"),
                S.formatBreak,
                S.list(S.string("memory"), S.int(1)),
            )
        ))
    }
}

class SExpressionTests {
    @Test
    fun intIsSerialised() {
        val expression = S.int(42)

        val string = expression.serialise()

        assertThat(string, equalTo("42"))
    }

    @Test
    fun stringWithoutSpecialCharactersIsSerialised() {
        val expression = S.string("Hello, world!")

        val string = expression.serialise()

        assertThat(string, equalTo("\"Hello, world!\""))
    }

    @Test
    fun emptyListIsJustParens() {
        val expression = S.list()

        val string = expression.serialise()

        assertThat(string, equalTo("()"))
    }

    @Test
    fun listSeparatesElementsWithSpace() {
        val expression = S.list(S.int(1), S.int(2), S.int(3))

        val string = expression.serialise()

        assertThat(string, equalTo("(1 2 3)"))
    }

    @Test
    fun formatBreakInListPutsRemainderOfElementsOnNewLines() {
        val expression = S.list(S.int(1), S.formatBreak, S.int(2), S.int(3))

        val string = expression.serialise()

        assertThat(string, equalTo("(1\n  2\n  3\n)"))
    }
}
