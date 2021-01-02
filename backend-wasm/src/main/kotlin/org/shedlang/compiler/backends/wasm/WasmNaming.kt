package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.ModuleName

internal object WasmNaming {

    fun moduleInit(moduleName: ModuleName) = "shed_module_init_${serialiseModuleName(moduleName)}"
    fun moduleValue(moduleName: ModuleName) = "shed_module_value_${serialiseModuleName(moduleName)}"

    val funcMainIdentifier = "shed_main"
    val funcStartIdentifier = "shed_start"

    private fun serialiseModuleName(moduleName: ModuleName) =
        moduleName.joinToString("_") { part -> part.value }

    object Runtime {
        val malloc = "shed_malloc"
        val heapPointer = "shed_malloc__heap_pointer"
        val heapEndPointer = "shed_malloc__heap_pointer_end"

        val stringAdd = "shed_string_add"
        val stringEquals = "shed_string_equals"
        val print = "shed_print"
    }

    object WasiImports {
        val fdWrite = "wasi_fd_write"
        val procExit = "wasi_proc_exit"
    }
}
