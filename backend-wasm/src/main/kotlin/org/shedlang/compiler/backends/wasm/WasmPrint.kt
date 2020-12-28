package org.shedlang.compiler.backends.wasm

internal fun generatePrintFunc(memory: WasmMemory): Pair<WasmMemory, SExpression> {
    val (memory2, stringContentsPointerMemoryIndex) = memory.staticAllocI32()
    val (memory3, stringLengthMemoryIndex) = memory2.staticAllocI32()
    val (memory4, nwrittenMemoryIndex) = memory3.staticAllocI32()

    val func = Wat.func(
        WasmCoreNames.print,
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

    return Pair(memory4, func)
}
