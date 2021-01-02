package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.ModuleName

internal object WasmNaming {
    fun moduleInit(moduleName: ModuleName) = "shed_module_init_${serialiseModuleName(moduleName)}"

    private fun serialiseModuleName(moduleName: ModuleName) =
        moduleName.joinToString("_") { part -> part.value }
}
