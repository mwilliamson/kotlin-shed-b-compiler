package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmFunction
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction

internal fun generateStringAddFunc(): WasmFunction {
    return Wasm.function(
        WasmCoreNames.stringAdd,
        params = listOf(Wasm.param("left", Wasm.T.i32), Wasm.param("right", Wasm.T.i32)),
        locals = listOf(
            Wasm.local("left_length", Wasm.T.i32),
            Wasm.local("right_length", Wasm.T.i32),
            Wasm.local("source_index", Wasm.T.i32),
            Wasm.local("result", Wasm.T.i32),
            Wasm.local("result_contents", Wasm.T.i32),
        ),
        results = listOf(Wasm.T.i32),
        body = listOf(
            Wasm.I.localGet("left"),
            Wasm.I.i32Load,
            Wasm.I.localSet("left_length"),

            Wasm.I.localGet("right"),
            Wasm.I.i32Load,
            Wasm.I.localSet("right_length"),

            Wasm.I.localGet("left_length"),
            Wasm.I.localGet("right_length"),
            Wasm.I.i32Add,
            Wasm.I.i32Const(4),
            Wasm.I.i32Add,
            Wasm.I.i32Const(4),
            Wasm.I.call(WasmCoreNames.malloc),
            Wasm.I.localSet("result"),

            Wasm.I.localGet("result"),
            Wasm.I.localGet("left_length"),
            Wasm.I.localGet("right_length"),
            Wasm.I.i32Add,
            Wasm.I.i32Store,

            Wasm.I.localGet("result"),
            Wasm.I.i32Const(4),
            Wasm.I.i32Add,
            Wasm.I.localSet("result_contents"),

            *copyStringContents(sourceIdentifier = "left").toTypedArray(),
            *copyStringContents(sourceIdentifier = "right").toTypedArray(),

            Wasm.I.localGet("result"),
        ),
    )
}

private fun copyStringContents(sourceIdentifier: String): List<WasmInstruction> {
    val loopIdentifier = "${sourceIdentifier}_copy"

    return listOf(
        Wasm.I.i32Const(0),
        Wasm.I.localSet("source_index"),
        Wasm.I.loop(loopIdentifier),

        Wasm.I.localGet("source_index"),
        Wasm.I.localGet("${sourceIdentifier}_length"),
        Wasm.I.i32GreaterThanOrEqualUnsigned,

        Wasm.I.if_(results = listOf()), // if_source_end

        Wasm.I.else_, // if_source_end

        Wasm.I.localGet("result_contents"),
        Wasm.I.localGet(sourceIdentifier),
        Wasm.I.i32Const(4),
        Wasm.I.i32Add,
        Wasm.I.localGet("source_index"),
        Wasm.I.i32Add,
        Wasm.I.i32Load8Unsigned,
        Wasm.I.i32Store8,

        Wasm.I.localGet("source_index"),
        Wasm.I.i32Const(1),
        Wasm.I.i32Add,
        Wasm.I.localSet("source_index"),
        Wasm.I.localGet("result_contents"),
        Wasm.I.i32Const(1),
        Wasm.I.i32Add,
        Wasm.I.localSet("result_contents"),

        Wasm.I.branch(loopIdentifier),

        Wasm.I.end, // if_source_end

        Wasm.I.end,
    )
}

internal fun generateStringEqualsFunc(): WasmFunction {
    return Wasm.function(
        WasmCoreNames.stringEquals,
        params = listOf(Wasm.param("left", Wasm.T.i32), Wasm.param("right", Wasm.T.i32)),
        locals = listOf(Wasm.local("index", Wasm.T.i32), Wasm.local("length", Wasm.T.i32)),
        results = listOf(Wasm.T.i32),
        body = listOf(
            Wasm.I.localGet("left"),
            Wasm.I.i32Load,

            Wasm.I.localGet("right"),
            Wasm.I.i32Load,

            Wasm.I.i32NotEqual,

            Wasm.I.if_(results = listOf(Wasm.T.i32)), // if_same_length

            Wasm.I.i32Const(0),

            Wasm.I.else_, // if_same_length

            Wasm.I.localGet("left"),
            Wasm.I.i32Load,
            Wasm.I.localSet("length"),

            Wasm.I.i32Const(0),
            Wasm.I.localSet("index"),

            Wasm.I.loop(identifier="iterate_chars", results = listOf(Wasm.T.i32)), // iterate_chars
            Wasm.I.localGet("index"),
            Wasm.I.localGet("length"),
            Wasm.I.i32GreaterThanOrEqualUnsigned,

            Wasm.I.if_(results = listOf(Wasm.T.i32)), // string_end

            Wasm.I.i32Const(1),

            Wasm.I.else_, // string_end

            // Get left char
            Wasm.I.localGet("left"),
            Wasm.I.i32Const(4),
            Wasm.I.i32Add,
            Wasm.I.localGet("index"),
            Wasm.I.i32Add,
            Wasm.I.i32Load8Unsigned,

            // Get right char
            Wasm.I.localGet("right"),
            Wasm.I.i32Const(4),
            Wasm.I.i32Add,
            Wasm.I.localGet("index"),
            Wasm.I.i32Add,
            Wasm.I.i32Load8Unsigned,

            // Compare chars
            Wasm.I.i32Equals,
            Wasm.I.if_(results = listOf(Wasm.T.i32)),
            Wasm.I.localGet("index"),
            Wasm.I.i32Const(1),
            Wasm.I.i32Add,
            Wasm.I.localSet("index"),
            Wasm.I.branch("iterate_chars"), // iterate_chars
            Wasm.I.else_,
            Wasm.I.i32Const(0),
            Wasm.I.end,

            Wasm.I.end, // string_end
            Wasm.I.end, // iterate_chars
            Wasm.I.end, // if_same_length
        ),
    )
}
