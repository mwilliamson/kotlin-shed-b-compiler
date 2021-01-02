package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.backends.wasm.WasmNaming

internal object Wasi {
    private val moduleName = "wasi_snapshot_preview1"
    val stdout = Wasm.I.i32Const(1)

    fun importFdWrite(): WasmImport {
        return Wasm.importFunction(
            moduleName = moduleName,
            entityName = "fd_write",
            identifier = WasmNaming.WasiImports.fdWrite,
            params = listOf(Wasm.T.i32, Wasm.T.i32, Wasm.T.i32, Wasm.T.i32),
            results = listOf(Wasm.T.i32),
        )
    }

    fun callFdWrite(
        fileDescriptor: WasmInstruction.Folded,
        iovs: WasmInstruction.Folded,
        iovsLen: WasmInstruction.Folded,
        nwritten: WasmInstruction.Folded,
    ): WasmInstruction {
        return Wasm.I.call(
            WasmNaming.WasiImports.fdWrite,
            args = listOf(
                fileDescriptor,
                iovs,
                iovsLen,
                nwritten
            ),
        )
    }

    fun importProcExit(): WasmImport {
        return Wasm.importFunction(
            moduleName = moduleName,
            entityName = "proc_exit",
            identifier = WasmNaming.WasiImports.procExit,
            params = listOf(Wasm.T.i32),
            results = listOf(),
        )
    }

    fun callProcExit(): WasmInstruction {
        return Wasm.I.call(identifier = WasmNaming.WasiImports.procExit)
    }
}
