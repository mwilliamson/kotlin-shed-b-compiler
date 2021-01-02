package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.Wasm

internal object WasmData {
    const val FUNCTION_POINTER_SIZE = 4
    const val VALUE_SIZE = 4
    val booleanType = Wasm.T.i32
    val functionPointerType = Wasm.T.i32
    val genericValueType = Wasm.T.i32
    val moduleValuePointerType = Wasm.T.i32
}
