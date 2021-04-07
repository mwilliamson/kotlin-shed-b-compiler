package org.shedlang.compiler.backends.wasm.wasm

import java.io.OutputStream

internal object WasmBinaryFormat {
    private val WASM_MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
    private val WASM_VERSION = byteArrayOf(0x01, 0x00, 0x00, 0x00)

    internal fun write(module: WasmModule, output: OutputStream) {
        output.write(WASM_MAGIC);
        output.write(WASM_VERSION);
    }
}
