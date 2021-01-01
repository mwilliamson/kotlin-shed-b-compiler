package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction

internal sealed class WasmStaticData(val alignment: Int?) {
    internal data class I32(val initial: WasmInstruction.Folded?): WasmStaticData(alignment = 4)
    internal data class Utf8String(val value: String): WasmStaticData(alignment = null)
}

const val WASM_PAGE_SIZE = 65536
