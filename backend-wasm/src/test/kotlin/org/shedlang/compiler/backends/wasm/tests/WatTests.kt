package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.wasm.wasm.S
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.Wat

class WatTests {
    @Test
    fun moduleHasMemoryDeclarations() {
        val module = Wasm.module()

        val expression = Wat.moduleToSExpression(module)

        assertThat(expression, equalTo(
            S.list(
                S.symbol("module"),
                S.formatBreak,
                S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(0)),
            )
        ))
    }

    @Test
    fun importsAppearBeforeMemoryDeclarations() {
        val module = Wasm.module(
            imports = listOf(
                Wasm.importFunction(
                    moduleName = "wasi_snapshot_preview1",
                    entityName = "fd_write",
                    identifier = "write",
                    params = listOf(),
                    results = listOf(Wasm.T.i32),
                )
            ),
        )

        val expression = Wat.moduleToSExpression(module)

        assertThat(expression, equalTo(
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
                        S.list(S.symbol("result"), S.symbol("i32")),
                    ),
                ),
                S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(0)),
            )
        ))
    }

    @Test
    fun importFunc() {
        val import = Wasm.importFunction(
            moduleName = "wasi_snapshot_preview1",
            entityName = "fd_write",
            identifier = "write",
            params = listOf(Wasm.T.i32, Wasm.T.i32),
            results = listOf(Wasm.T.i32),
        )

        val expression = Wat.importToSExpression(import)

        assertThat(expression, equalTo(
            S.list(
                S.symbol("import"),
                S.string("wasi_snapshot_preview1"),
                S.string("fd_write"),
                S.list(
                    S.symbol("func"),
                    S.identifier("write"),
                    S.list(S.symbol("param"), S.symbol("i32"), S.symbol("i32")),
                    S.list(S.symbol("result"), S.symbol("i32")),
                ),
            ),
        ))
    }

    @Test
    fun data() {
        val dataSegment = Wasm.dataSegment(
            offset = 8,
            bytes = "Hello, world!\n".toByteArray(),
        )

        val expression = Wat.dataSegmentToSExpression(dataSegment)

        assertThat(expression, equalTo(
            S.list(
                S.symbol("data"),
                S.list(S.symbol("i32.const"), S.int(8)),
                S.string("Hello, world!\n"),
            ),
        ))
    }

    @Test
    fun funcIncludesNameAndBody() {
        val func = Wasm.function(
            identifier = "main",
            body = listOf(
                Wasm.I.drop,
            ),
        )

        val expression = Wat.functionToSExpression(func)

        assertThat(expression, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.list(S.symbol("result")),
                S.formatBreak,
                S.symbol("drop"),
            ),
        ))
    }

    @Test
    fun funcIncludesResultTypeIfSet() {
        val func = Wasm.function(
            identifier = "main",
            results = listOf(Wasm.T.i32),
            body = listOf(
                Wasm.I.drop,
            ),
        )

        val expression = Wat.functionToSExpression(func)

        assertThat(expression, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.list(S.symbol("result"), S.symbol("i32")),
                S.formatBreak,
                S.symbol("drop"),
            ),
        ))
    }

    @Test
    fun funcHasOptionalExport() {
        val func = Wasm.function(
            identifier = "main",
            exportName = "_start",
            body = listOf(
                Wasm.I.drop,
            ),
        )

        val expression = Wat.functionToSExpression(func)

        assertThat(expression, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.list(S.symbol("export"), S.string("_start")),
                S.list(S.symbol("result")),
                S.formatBreak,
                S.symbol("drop")
            ),
        ))
    }

    @Test
    fun funcHasOptionalLocals() {
        val func = Wasm.function(
            identifier = "main",
            locals = listOf(Wasm.local("local_1", Wasm.T.i32)),
            body = listOf(
                Wasm.I.drop,
            ),
        )

        val expression = Wat.functionToSExpression(func)

        assertThat(expression, equalTo(
            S.list(
                S.symbol("func"),
                S.identifier("main"),
                S.list(S.symbol("result")),
                S.formatBreak,
                S.list(S.symbol("local"), S.identifier("local_1"), S.symbol("i32")),
                S.symbol("drop"),
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
