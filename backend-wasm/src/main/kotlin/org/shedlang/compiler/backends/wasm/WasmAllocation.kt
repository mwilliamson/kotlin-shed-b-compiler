package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.runtime.callMalloc
import org.shedlang.compiler.backends.wasm.wasm.Wasm

internal fun malloc(
    localName: String,
    layout: WasmObjects.Layout,
    context: WasmFunctionContext
): Pair<WasmFunctionContext, String> {
    val (context2, uniqueName) = context.addLocal(localName)
    val context3 = context2.addInstruction(
        Wasm.I.localSet(
            uniqueName,
            callMalloc(
                size = layout.size,
                alignment = layout.alignment,
            ),
        )
    )
    return Pair(context3, uniqueName)
}
