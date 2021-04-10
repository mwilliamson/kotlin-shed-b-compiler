package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.backends.wasm.add

internal class WasmSymbolTable {
    private val funcIndices = mutableMapOf<String, Int>()

    fun addFuncIndex(name: String) {
        funcIndices.add(name, funcIndices.size)
    }

    fun funcIndex(name: String): Int {
        return funcIndices.getValue(name)
    }
}
