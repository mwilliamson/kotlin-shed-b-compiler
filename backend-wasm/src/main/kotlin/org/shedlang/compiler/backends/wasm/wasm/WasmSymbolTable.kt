package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.backends.wasm.add

internal class WasmSymbolTable {
    companion object {
        fun forModule(module: WasmModule): WasmSymbolTable {
            val symbolTable = WasmSymbolTable()

            for (import in module.imports) {
                if (import.descriptor is WasmImportDescriptor.Function) {
                    symbolTable.addFuncIndex(import.identifier)
                }
            }

            for (function in module.functions) {
                symbolTable.addFuncIndex(function.identifier)
            }

            return symbolTable
        }
    }

    private val funcIndices = mutableMapOf<String, Int>()

    private fun addFuncIndex(name: String) {
        funcIndices.add(name, funcIndices.size)
    }

    fun funcIndex(name: String): Int {
        return funcIndices.getValue(name)
    }
}
