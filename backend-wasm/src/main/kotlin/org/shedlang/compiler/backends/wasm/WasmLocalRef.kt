package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction

internal class WasmLocalRef(private val name: String) {
    fun get(): WasmInstruction.Folded {
        return Wasm.I.localGet(name)
    }

    fun set(): WasmInstruction {
        return Wasm.I.localSet(name)
    }

    fun set(value: WasmInstruction.Folded): WasmInstruction {
        return Wasm.I.localSet(name, value)
    }
}
