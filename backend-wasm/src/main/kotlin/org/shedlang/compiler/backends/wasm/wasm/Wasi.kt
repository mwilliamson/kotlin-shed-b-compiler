package org.shedlang.compiler.backends.wasm.wasm

internal object Wasi {
    val stdout = Wasm.I.i32Const(1)

    fun importFdWrite(identifier: String): WasmImport {
        return Wasm.importFunction(
            moduleName = "wasi_snapshot_preview1",
            entityName = "fd_write",
            identifier = identifier,
            params = listOf(Wasm.T.i32, Wasm.T.i32, Wasm.T.i32, Wasm.T.i32),
            results = listOf(Wasm.T.i32),
        )
    }

    fun callFdWrite(
        identifier: String,
        fileDescriptor: WasmInstruction.Folded,
        iovs: WasmInstruction.Folded,
        iovsLen: WasmInstruction.Folded,
        nwritten: WasmInstruction.Folded,
    ): WasmInstruction {
        return Wasm.I.call(
            identifier,
            args = listOf(
                fileDescriptor,
                iovs,
                iovsLen,
                nwritten
            ),
        )
    }
}
