package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.runtime.callMalloc

internal fun malloc(
    localName: String,
    layout: WasmObjects.Layout,
    context: WasmFunctionContext
): Pair<WasmFunctionContext, WasmLocalRef> {
    val (context2, uniqueName) = context.addLocal(localName)
    val context3 = context2.addInstruction(
        uniqueName.set(
            callMalloc(
                size = layout.size,
                alignment = layout.alignment,
            ),
        )
    )
    return Pair(context3, uniqueName)
}
