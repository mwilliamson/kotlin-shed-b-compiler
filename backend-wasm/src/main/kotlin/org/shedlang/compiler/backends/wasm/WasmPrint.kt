package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasi
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmFunction
import org.shedlang.compiler.backends.wasm.wasm.Wat

internal fun generatePrintFunc(memory: WasmMemory): Pair<WasmMemory, WasmFunction> {
    val (memory2, stringContentsPointerMemoryIndex) = memory.staticAllocI32()
    val (memory3, stringLengthMemoryIndex) = memory2.staticAllocI32()
    val (memory4, nwrittenMemoryIndex) = memory3.staticAllocI32()

    val func = Wasm.function(
        WasmCoreNames.print,
        params = listOf(Wasm.param("string", Wasm.T.i32)),
        body = listOf(
            Wasm.I.i32Const(stringContentsPointerMemoryIndex),
            Wasm.I.localGet("string"),
            Wasm.I.i32Const(4),
            Wasm.I.i32Add,
            Wasm.I.i32Store,

            Wasm.I.i32Const(stringLengthMemoryIndex),
            Wasm.I.localGet("string"),
            Wasm.I.i32Load,
            Wasm.I.i32Store,

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

    return Pair(memory4, func)
}
