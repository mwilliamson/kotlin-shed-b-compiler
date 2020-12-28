package org.shedlang.compiler.backends.wasm

internal fun generateStringAddFunc(): SExpression {
    return Wat.func(
        WasmCoreNames.stringAdd,
        params = listOf(Wat.param("left", Wat.i32), Wat.param("right", Wat.i32)),
        locals = listOf(
            Wat.local("left_length", Wat.i32),
            Wat.local("right_length", Wat.i32),
            Wat.local("source_index", Wat.i32),
            Wat.local("result", Wat.i32),
            Wat.local("result_contents", Wat.i32),
        ),
        result = Wat.i32,
        body = listOf(
            Wat.I.localGet("left"),
            Wat.I.i32Load,
            Wat.I.localSet("left_length"),

            Wat.I.localGet("right"),
            Wat.I.i32Load,
            Wat.I.localSet("right_length"),

            Wat.I.localGet("left_length"),
            Wat.I.localGet("right_length"),
            Wat.I.i32Add,
            Wat.I.i32Const(4),
            Wat.I.i32Add,
            Wat.I.i32Const(4),
            Wat.I.call(WasmCoreNames.malloc),
            Wat.I.localSet("result"),

            Wat.I.localGet("result"),
            Wat.I.localGet("left_length"),
            Wat.I.localGet("right_length"),
            Wat.I.i32Add,
            Wat.I.i32Store,

            Wat.I.localGet("result"),
            Wat.I.i32Const(4),
            Wat.I.i32Add,
            Wat.I.localSet("result_contents"),

            *copyStringContents(sourceIdentifier = "left").toTypedArray(),
            *copyStringContents(sourceIdentifier = "right").toTypedArray(),

            Wat.I.localGet("result"),
        ),
    )
}

private fun copyStringContents(sourceIdentifier: String): List<SExpression> {
    val loopIdentifier = "${sourceIdentifier}_copy"

    return listOf(
        Wat.I.i32Const(0),
        Wat.I.localSet("source_index"),
        *Wat.I.loop(loopIdentifier, result = listOf()).toTypedArray(),

        Wat.I.localGet("source_index"),
        Wat.I.localGet("${sourceIdentifier}_length"),
        Wat.I.i32GeU,

        *Wat.I.if_(result = listOf()).toTypedArray(), // if_source_end

        Wat.I.else_, // if_source_end

        Wat.I.localGet("result_contents"),
        Wat.I.localGet(sourceIdentifier),
        Wat.I.i32Const(4),
        Wat.I.i32Add,
        Wat.I.localGet("source_index"),
        Wat.I.i32Add,
        Wat.I.i32Load8U,
        Wat.I.i32Store8,

        Wat.I.localGet("source_index"),
        Wat.I.i32Const(1),
        Wat.I.i32Add,
        Wat.I.localSet("source_index"),
        Wat.I.localGet("result_contents"),
        Wat.I.i32Const(1),
        Wat.I.i32Add,
        Wat.I.localSet("result_contents"),

        Wat.I.br(loopIdentifier),

        Wat.I.end, // if_source_end

        Wat.I.end,
    )
}

internal fun generateStringEqualsFunc(): SExpression {
    return Wat.func(
        WasmCoreNames.stringEquals,
        params = listOf(Wat.param("left", Wat.i32), Wat.param("right", Wat.i32)),
        locals = listOf(Wat.local("index", Wat.i32), Wat.local("length", Wat.i32)),
        result = Wat.i32,
        body = listOf(
            Wat.I.localGet("left"),
            Wat.I.i32Load,

            Wat.I.localGet("right"),
            Wat.I.i32Load,

            Wat.I.i32Ne,

            *Wat.I.if_(result = listOf(Wat.i32)).toTypedArray(), // if_same_length

            Wat.I.i32Const(0),

            Wat.I.else_, // if_same_length

            Wat.I.localGet("left"),
            Wat.I.i32Load,
            Wat.I.localSet("length"),

            Wat.I.i32Const(0),
            Wat.I.localSet("index"),

            *Wat.I.loop(identifier="iterate_chars", result = listOf(Wat.i32)).toTypedArray(), // iterate_chars
            Wat.I.localGet("index"),
            Wat.I.localGet("length"),
            Wat.I.i32GeU,

            *Wat.I.if_(result = listOf(Wat.i32)).toTypedArray(), // string_end

            Wat.I.i32Const(1),

            Wat.I.else_, // string_end

            // Get left char
            Wat.I.localGet("left"),
            Wat.I.i32Const(4),
            Wat.I.i32Add,
            Wat.I.localGet("index"),
            Wat.I.i32Add,
            Wat.I.i32Load8U,

            // Get right char
            Wat.I.localGet("right"),
            Wat.I.i32Const(4),
            Wat.I.i32Add,
            Wat.I.localGet("index"),
            Wat.I.i32Add,
            Wat.I.i32Load8U,

            // Compare chars
            Wat.I.i32Eq,
            *Wat.I.if_(result = listOf(Wat.i32)).toTypedArray(),
            Wat.I.localGet("index"),
            Wat.I.i32Const(1),
            Wat.I.i32Add,
            Wat.I.localSet("index"),
            Wat.I.br("iterate_chars"), // iterate_chars
            Wat.I.else_,
            Wat.I.i32Const(0),
            Wat.I.end,

            Wat.I.end, // string_end
            Wat.I.end, // iterate_chars
            Wat.I.end, // if_same_length
        ),
    )
}
