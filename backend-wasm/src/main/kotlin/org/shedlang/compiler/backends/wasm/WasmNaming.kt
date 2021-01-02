package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.ModuleName

internal object WasmNaming {
    val heapPointer = "shed_heap_pointer"
    val heapEndPointer = "shed_heap_pointer_end"

    fun moduleInit(moduleName: ModuleName) = "shed_module_init_${serialiseModuleName(moduleName)}"
    fun moduleValue(moduleName: ModuleName) = "shed_module_value_${serialiseModuleName(moduleName)}"

    val funcMainIdentifier = "shed_main"
    val funcStartIdentifier = "shed_start"

    private fun serialiseModuleName(moduleName: ModuleName) =
        moduleName.joinToString("_") { part -> part.value }

    object WasiImports {
        val fdWrite = "wasi_fd_write"
        val procExit = "wasi_proc_exit"
    }
}
