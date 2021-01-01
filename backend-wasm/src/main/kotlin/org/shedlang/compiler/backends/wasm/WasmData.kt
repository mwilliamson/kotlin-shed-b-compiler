package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.Wasm

internal object WasmData {
    const val VALUE_SIZE = 4
    val booleanType = Wasm.T.i32
    val genericValueType = Wasm.T.i32
}
