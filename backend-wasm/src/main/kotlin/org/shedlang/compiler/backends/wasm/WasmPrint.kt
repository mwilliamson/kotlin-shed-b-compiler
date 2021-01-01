package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.Wasi
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmFunction

internal fun generatePrintFunc(): Pair<WasmGlobalContext, WasmFunction> {
    val global = WasmGlobalContext.initial()
    val (global2, stringContentsPointerMemoryIndex) = global.addStaticI32()
    val (global3, stringLengthMemoryIndex) = global2.addStaticI32()
    val (global4, nwrittenMemoryIndex) = global3.addStaticI32()

    val func = Wasm.function(
        WasmCoreNames.print,
        params = listOf(Wasm.param("string", Wasm.T.i32)),
        body = listOf(
            Wasm.I.i32Store(
                Wasm.I.i32Const(stringContentsPointerMemoryIndex),
                Wasm.I.i32Add(
                    Wasm.I.localGet("string"),
                    Wasm.I.i32Const(4),
                ),
            ),

            Wasm.I.i32Store(
                Wasm.I.i32Const(stringLengthMemoryIndex),
                Wasm.I.i32Load(Wasm.I.localGet("string")),
            ),

            Wasi.callFdWrite(
                identifier = "fd_write",
                fileDescriptor = Wasi.stdout,
                iovs = Wasm.I.i32Const(stringContentsPointerMemoryIndex),
                iovsLen = Wasm.I.i32Const(1),
                nwritten = Wasm.I.i32Const(nwrittenMemoryIndex),
            ),
            Wasm.I.drop,
        ),
    )

    return Pair(global4, func)
}
