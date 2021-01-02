package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.ModuleName

internal object WasmNaming {
    val heapPointer = "shed_heap_pointer"
    val heapEndPointer = "shed_heap_pointer_end"

    fun moduleInit(moduleName: ModuleName) = "shed_module_init_${serialiseModuleName(moduleName)}"

    private fun serialiseModuleName(moduleName: ModuleName) =
        moduleName.joinToString("_") { part -> part.value }
}
