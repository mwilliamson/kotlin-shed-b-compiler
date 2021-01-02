package org.shedlang.compiler.backends.wasm.runtime

import org.shedlang.compiler.backends.wasm.WasmGlobalContext

internal fun compileRuntime(): WasmGlobalContext {
    return WasmGlobalContext.merge(listOf(
        generateMalloc(),
        generatePrintFunc(),
        generateStringEqualsFunc(),
        generateStringAddFunc(),
    ))
}
