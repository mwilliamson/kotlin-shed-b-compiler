package org.shedlang.compiler.backends.wasm.runtime

import org.shedlang.compiler.backends.wasm.WasmGlobalContext
import org.shedlang.compiler.backends.wasm.WasmNaming
import org.shedlang.compiler.backends.wasm.wasm.Wasi
import org.shedlang.compiler.backends.wasm.wasm.Wasm

internal fun generatePrintFunc(): WasmGlobalContext {
    val global = WasmGlobalContext.initial()
    val (global2, stringContentsPointerMemoryIndex) = global.addStaticData(size = 8, alignment = 4)
    val (global3, nwrittenMemoryIndex) = global2.addStaticI32()

    val func = Wasm.function(
        WasmNaming.Runtime.print,
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
                Wasm.I.i32Add(
                    Wasm.I.i32Const(stringContentsPointerMemoryIndex),
                    Wasm.I.i32Const(4),
                ),
                Wasm.I.i32Load(Wasm.I.localGet("string")),
            ),

            Wasi.callFdWrite(
                fileDescriptor = Wasi.stdout,
                iovs = Wasm.I.i32Const(stringContentsPointerMemoryIndex),
                iovsLen = Wasm.I.i32Const(1),
                nwritten = Wasm.I.i32Const(nwrittenMemoryIndex),
            ),
            Wasm.I.drop,
        ),
    )

    return global3.addStaticFunction(func)
}
