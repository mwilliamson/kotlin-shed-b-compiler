package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.backends.wasm.add

internal class WasmSymbolTable {
    companion object {
        fun forModule(module: WasmModule): WasmSymbolTable {
            val symbolTable = WasmSymbolTable()

            for (import in module.imports) {
                if (import.descriptor is WasmImportDescriptor.Function) {
                    symbolTable.addFunction(import.identifier, importedFunctionSymbolInfo(import))
                }
            }

            for (function in module.functions) {
                symbolTable.addFunction(function.identifier, definedFunctionSymbolInfo(function))
            }

            for (global in module.globals) {
                symbolTable.addGlobal(global, WasmSymbolInfo.Global(flags = 0, identifier = global.identifier))
            }

            module.dataSegments.forEachIndexed { dataSegmentIndex, dataSegment ->
                symbolTable.addSymbolInfo(WasmSymbolInfo.Data(
                    flags = 0,
                    identifier = "DATA_$dataSegmentIndex",
                    dataSegmentIndex = dataSegmentIndex,
                    offset = 0,
                    size = dataSegment.bytes.size,
                ))
            }

            for (tableEntry in module.table) {
                symbolTable.addTableEntryIndex(tableEntry)
            }

            return symbolTable
        }

        private fun importedFunctionSymbolInfo(import: WasmImport): WasmSymbolInfo.Function {
            val flags = WasmSymbolFlags.UNDEFINED.id or WasmSymbolFlags.EXPLICIT_NAME.id
            return WasmSymbolInfo.Function(identifier = import.identifier, flags = flags)
        }

        private fun definedFunctionSymbolInfo(function: WasmFunction): WasmSymbolInfo.Function {
            var flags = 0

            if (function.export) {
                flags = flags or WasmSymbolFlags.EXPORTED.id
            }

            return WasmSymbolInfo.Function(identifier = function.identifier, flags = flags)
        }
    }

    private val functionIndices = mutableMapOf<String, Int>()
    private val functionSymbolIndices = mutableMapOf<Int, Int>()
    private val globalIndices = mutableMapOf<String, Int>()
    private val globalSymbolIndices = mutableMapOf<Int, Int>()
    private val tableEntryIndices = mutableMapOf<String, Int>()
    private val symbolInfos = mutableListOf<WasmSymbolInfo>()

    private fun addFunction(name: String, symbolInfo: WasmSymbolInfo.Function) {
        val functionIndex = functionIndices.size
        functionIndices.add(name, functionIndex)
        val symbolIndex = symbolInfos.size
        functionSymbolIndices.add(functionIndex, symbolIndex)
        symbolInfos.add(symbolInfo)
    }

    fun functionIndex(name: String): Int {
        return functionIndices.getValue(name)
    }

    fun functionIndexToSymbolIndex(functionIndex: Int): Int {
        return functionSymbolIndices.getValue(functionIndex)
    }

    private fun addGlobal(global: WasmGlobal, symbolInfo: WasmSymbolInfo.Global) {
        val globalIndex = globalIndices.size
        globalIndices.add(global.identifier, globalIndex)
        val symbolIndex = symbolInfos.size
        globalSymbolIndices.add(globalIndex, symbolIndex)
        symbolInfos.add(symbolInfo)
    }

    fun globalIndexToSymbolIndex(globalIndex: Int): Int {
        return globalSymbolIndices.getValue(globalIndex)
    }

    private fun addTableEntryIndex(name: String) {
        tableEntryIndices.add(name, tableEntryIndices.size)
    }

    fun tableEntryIndex(name: String): Int {
        return tableEntryIndices.getValue(name)
    }

    private fun addSymbolInfo(info: WasmSymbolInfo) {
        symbolInfos.add(info)
    }

    fun symbolInfos(): List<WasmSymbolInfo> {
        return symbolInfos
    }
}

internal sealed class WasmSymbolInfo(val flags: Int) {
    class Data(flags: Int, val identifier: String, val dataSegmentIndex: Int, val offset: Int, val size: Int) : WasmSymbolInfo(flags)
    class Function(flags: Int, val identifier: String) : WasmSymbolInfo(flags)
    class Global(flags: Int, val identifier: String) : WasmSymbolInfo(flags)
}

internal enum class WasmSymbolFlags(val id: Int) {
    BINDING_WEAK(1),
    BINDING_LOCAL(2),
    VISIBILITY_HIDDEN(4),
    UNDEFINED(0x10),
    EXPORTED(0x20),
    EXPLICIT_NAME(0x40),
    NO_STRIP(0x80),
}
