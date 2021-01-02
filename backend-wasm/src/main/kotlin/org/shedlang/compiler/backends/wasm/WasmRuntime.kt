package org.shedlang.compiler.backends.wasm

internal fun compileRuntime(): WasmGlobalContext {
    return WasmGlobalContext.merge(listOf(
        generateMalloc(),
        generatePrintFunc(),
        generateStringEqualsFunc(),
        generateStringAddFunc(),
    ))
}
