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
                S.symbol("module"),
                S.formatBreak,
                S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(0)),
            )
        ))
    }

    @Test
    fun importsAppearBeforeMemoryDeclarations() {
        val module = Wat.module(
            imports = listOf(
                Wat.importFunc(
                    moduleName = "wasi_snapshot_preview1",
                    exportName = "fd_write",
                    identifier = "write",
                    params = listOf(),
                    result = Wat.i32,
                )
            ),
        )

        assertThat(module, equalTo(
            S.list(
                S.symbol("module"),
                S.formatBreak,
                S.list(
                    S.symbol("import"),
                    S.string("wasi_snapshot_preview1"),
                    S.string("fd_write"),
                    S.list(
                        S.symbol("func"),
                        S.identifier("write"),
                        S.list(S.symbol("param")),
                        S.list(S.symbol("result"), Wat.i32),
                    ),
                ),
                S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(0)),
            )
        ))
    }

    @Test
    fun importFunc() {
        val import = Wat.importFunc(
            moduleName = "wasi_snapshot_preview1",
            exportName = "fd_write",
            identifier = "write",
            params = listOf(Wat.i32, Wat.i32),
            result = Wat.i32,
        )

        assertThat(import, equalTo(
            S.list(
                S.symbol("import"),
                S.string("wasi_snapshot_preview1"),
                S.string("fd_write"),
                S.list(
                    S.symbol("func"),
                    S.identifier("write"),
                    S.list(S.symbol("param"), Wat.i32, Wat.i32),
                    S.list(S.symbol("result"), Wat.i32),
                ),
            ),
        ))
    }

    @Test
    fun data() {
        val data = Wat.data(
            offset = 8,
            value = "Hello, world!\n",
        )

        assertThat(data, equalTo(
            S.list(
                S.symbol("data"),
                Wat.I.i32Const(8),
                S.string("Hello, world!\n"),
            ),
        ))
    }

    @Test
    fun funcIncludesNameAndBody() {
        val func = Wat.func(
            identifier = "main",
            body = listOf(
                Wat.I.drop,
            ),
        )

        assertThat(func, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.formatBreak,
                Wat.I.drop,
            ),
        ))
    }

    @Test
    fun funcIncludesResultTypeIfSet() {
        val func = Wat.func(
            identifier = "main",
            result = Wat.i32,
            body = listOf(
                Wat.I.drop,
            ),
        )

        assertThat(func, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.list(S.symbol("result"), Wat.i32),
                S.formatBreak,
                Wat.I.drop,
            ),
        ))
    }

    @Test
    fun funcHasOptionalExport() {
        val func = Wat.func(
            identifier = "main",
            exportName = "_start",
            body = listOf(
                Wat.I.drop,
            ),
        )

        assertThat(func, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.list(S.symbol("export"), S.string("_start")),
                S.formatBreak,
                Wat.I.drop,
            ),
        ))
    }

    @Test
    fun funcHasOptionalLocals() {
        val func = Wat.func(
            identifier = "main",
            locals = listOf(Wat.local("local_1", Wat.i32)),
            body = listOf(
                Wat.I.drop,
            ),
        )

        assertThat(func, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.formatBreak,
                S.list(S.symbol("local"), S.identifier("local_1"), Wat.i32),
                Wat.I.drop,
            ),
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
    fun specialCharactersInStringAreEscaped() {
        val expression = S.string("Hello, world!\n")

        val string = expression.serialise()

        assertThat(string, equalTo("\"Hello, world!\\n\""))
    }

    @Test
    fun symbolWithoutSpecialCharactersIsSerialised() {
        val expression = S.symbol("module")

        val string = expression.serialise()

        assertThat(string, equalTo("module"))
    }

    @Test
    fun identifierIsPrefixedWithDollarSign() {
        val expression = S.identifier("fd_write")

        val string = expression.serialise()

        assertThat(string, equalTo("\$fd_write"))
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

    @Test
    fun indentationCanBeNested() {
        val expression = S.list(
            S.int(1),
            S.formatBreak,
            S.list(
                S.int(2),
                S.formatBreak,
                S.int(3),
            ),
        )

        val string = expression.serialise()

        assertThat(string, equalTo("(1\n  (2\n    3\n  )\n)"))
    }
}