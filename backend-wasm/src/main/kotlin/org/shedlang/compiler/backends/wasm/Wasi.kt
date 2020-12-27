package org.shedlang.compiler.backends.wasm

internal object Wasi {
    val stdout = Wat.I.i32Const(1)

    fun importFdWrite(identifier: String): SExpression {
        return Wat.importFunc(
            moduleName = "wasi_snapshot_preview1",
            exportName = "fd_write",
            identifier = identifier,
            params = listOf(Wat.i32, Wat.i32, Wat.i32, Wat.i32),
            result = Wat.i32,
        )
    }

    fun callFdWrite(identifier: String, fileDescriptor: SExpression, iovs: SExpression, iovsLen: SExpression, nwritten: SExpression): SExpression {
        return Wat.I.call(
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
