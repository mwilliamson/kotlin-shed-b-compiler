package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.ShedRuntime
import org.shedlang.compiler.types.UserDefinedEffect

internal object WasmNaming {
    fun moduleInit(moduleName: ModuleName) = "shed_module_init_${serialiseModuleName(moduleName)}"
    fun moduleIsInited(moduleName: ModuleName) = "shed_module_is_inited_${serialiseModuleName(moduleName)}"
    fun moduleValue(moduleName: ModuleName) = "shed_module_value_${serialiseModuleName(moduleName)}"

    fun effectTagName(effect: UserDefinedEffect) = "shed_effect_tag_${effect.name.value}"

    val funcStartIdentifier = "shed_start"

    private fun serialiseModuleName(moduleName: ModuleName) =
        moduleName.joinToString("_") { part -> part.value }

    val environmentPointer = "shed_environment"

    object Runtime {
        val malloc = "shed_malloc"
        val heapPointer = "shed_malloc__heap_pointer"
        val heapEndPointer = "shed_malloc__heap_pointer_end"

        val stringAdd = ShedRuntime.stringAdd
        val stringEquals = ShedRuntime.stringEquals
        val print = "shed_print"
    }

    object Wasi {
        val start = "_start"
        val fdWrite = "wasi_fd_write"
        val procExit = "wasi_proc_exit"
    }
}
