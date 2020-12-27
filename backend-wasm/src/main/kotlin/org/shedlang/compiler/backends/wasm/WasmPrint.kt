package org.shedlang.compiler.backends.wasm

internal fun generatePrintFunc(identifier: String, context: WasmFunctionContext): Pair<WasmFunctionContext, SExpression> {
    val (context2, stringContentsPointerMemoryIndex) = context.staticAllocI32()
    val (context3, stringLengthMemoryIndex) = context2.staticAllocI32()
    val (context4, nwrittenMemoryIndex) = context3.staticAllocI32()

    val func = Wat.func(
        identifier,
        params = listOf(Wat.param("string", Wat.i32)),
        body = listOf(
            Wat.I.i32Const(stringContentsPointerMemoryIndex),
            Wat.I.localGet("string"),
            Wat.I.i32Const(4),
            Wat.I.i32Add,
            Wat.I.i32Store,

            Wat.I.i32Const(stringLengthMemoryIndex),
            Wat.I.localGet("string"),
            Wat.I.i32Load,
            Wat.I.i32Store,

            Wasi.callFdWrite(
                identifier = "fd_write",
                fileDescriptor = Wasi.stdout,
                iovs = Wat.I.i32Const(stringContentsPointerMemoryIndex),
                iovsLen = Wat.I.i32Const(1),
                nwritten = Wat.I.i32Const(nwrittenMemoryIndex),
            ),
            Wat.I.drop,
        ),
    )

    return Pair(context4, func)
}
