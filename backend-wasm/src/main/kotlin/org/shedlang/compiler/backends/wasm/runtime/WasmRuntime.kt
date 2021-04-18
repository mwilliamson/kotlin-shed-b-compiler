package org.shedlang.compiler.backends.wasm.runtime

import org.shedlang.compiler.backends.wasm.WasmData
import org.shedlang.compiler.backends.wasm.WasmGlobalContext
import org.shedlang.compiler.backends.wasm.WasmNaming
import org.shedlang.compiler.backends.wasm.wasm.Wasm

internal fun compileRuntime(): WasmGlobalContext {
    return WasmGlobalContext.merge(listOf(
        generateMalloc(),
        generatePrintFunc(),
    ))
        .addImport(Wasm.importFunction(
            moduleName = "env",
            entityName = WasmNaming.Runtime.stringAdd,
            identifier = WasmNaming.Runtime.stringAdd,
            params = listOf(WasmData.stringType, WasmData.stringType),
            results = listOf(WasmData.stringType),
        ))
        .addImport(Wasm.importFunction(
            moduleName = "env",
            entityName = WasmNaming.Runtime.stringEquals,
            identifier = WasmNaming.Runtime.stringEquals,
            params = listOf(WasmData.stringType, WasmData.stringType),
            results = listOf(WasmData.booleanType),
        ))
}
