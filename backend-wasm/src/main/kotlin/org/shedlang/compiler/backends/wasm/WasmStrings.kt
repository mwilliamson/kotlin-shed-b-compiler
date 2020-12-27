package org.shedlang.compiler.backends.wasm

internal fun generateStringEqualsFunc(identifier: String): SExpression {
    return Wat.func(
        identifier,
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
