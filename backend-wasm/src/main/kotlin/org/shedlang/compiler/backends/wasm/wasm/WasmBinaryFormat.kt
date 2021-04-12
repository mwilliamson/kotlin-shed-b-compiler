package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.NullSource
import org.shedlang.compiler.backends.wasm.LateIndex
import org.shedlang.compiler.backends.wasm.add
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

internal object WasmBinaryFormat {
    internal fun writeModule(module: WasmModule, output: OutputStream, lateIndices: Map<LateIndex, Int>) {
        val buffer = BufferWriter()
        val writer = WasmBinaryFormatWriter(
            lateIndices = lateIndices,
            symbolTable = WasmSymbolTable.forModule(module),
            objectFile = false,
            output = buffer,
        )
        writer.write(module)
        buffer.writeTo(output)
    }

    internal fun writeObjectFile(module: WasmModule, output: OutputStream, lateIndices: Map<LateIndex, Int>) {
        val buffer = BufferWriter()
        val writer = WasmBinaryFormatWriter(
            lateIndices = lateIndices,
            symbolTable = WasmSymbolTable.forModule(module),
            objectFile = true,
            output = buffer,
        )
        writer.write(module)
        buffer.writeTo(output)
    }
}

@ExperimentalUnsignedTypes
private class WasmBinaryFormatWriter(
    private val lateIndices: Map<LateIndex, Int>,
    private val symbolTable: WasmSymbolTable,
    private val objectFile: Boolean,
    private val output: BufferWriter,
) {
    private val WASM_MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
    private val WASM_VERSION = byteArrayOf(0x01, 0x00, 0x00, 0x00)
    private val labelStack = mutableListOf<String?>()
    private var localIndices = mutableMapOf<String, Int>()
    private val typeIndices = mutableMapOf<WasmFuncType, Int>()
    private val relocationEntries = mutableListOf<RelocationEntry>()
    private var currentSectionType: SectionType? = null
    private var currentSectionIndex = -1
    private var currentSectionStartPosition = -1

    private enum class SectionType(val id: Byte) {
        CUSTOM(0),
        TYPE(1),
        IMPORT(2),
        FUNCTION(3),
        TABLE(4),
        MEMORY(5),
        GLOBAL(6),
        EXPORT(7),
        START(8),
        ELEMENT(9),
        CODE(10),
        DATA(11),
        DATA_COUNT(12),
    }

    private enum class LinkingSubsectionType(val id: Byte) {
        WASM_SEGMENT_INFO(5),
        WASM_INIT_FUNCS(6),
        WASM_COMDAT_INFO(7),
        WASM_SYMBOL_TABLE(8),

    }

    private enum class SymbolType(val id: Byte) {
        FUNCTION(0),
        DATA(1),
        GLOBAL(2),
        SECTION(3),
        EVENT(4),
        TABLE(5),
    }

    private enum class RelocationType(val id: Byte, val isSigned: Boolean) {
        FUNCTION_INDEX_LEB(0, isSigned = false),
        TABLE_INDEX_SLEB(1, isSigned = true),
        TABLE_INDEX_I32(2, isSigned = true),
        MEMORY_ADDR_LEB(3, isSigned = false),
        MEMORY_ADDR_SLEB(4, isSigned = true),
        MEMORY_ADDR_I32(5, isSigned = true),
        TYPE_INDEX_LEB(6, isSigned = false),
        GLOBAL_INDEX_LEB(7, isSigned = false),
        FUNCTION_OFFSET_I32(8, isSigned = true),
        SECTION_OFFSET_I32(9, isSigned = true),
        EVENT_INDEX_LEB(10, isSigned = false),
        GLOBAL_INDEX_I32(13, isSigned = true),
        MEMORY_ADDR_LEB64(14, isSigned = false),
        MEMORY_ADDR_SLEB64(15, isSigned = true),
        MEMORY_ADDR_I64(16, isSigned = true),
        MEMORY_ADDR_REL_SLEB64(17, isSigned = true),
        TABLE_INDEX_SLEB64(18, isSigned = true),
        TABLE_INDEX_I64(19, isSigned = true),
        TABLE_NUMBER_LEB(20, isSigned = false),
    }

    private class RelocationEntry(val sectionIndex: Int, val type: RelocationType, val offset: Int, val index: Int, val addend: Int?)

    internal fun write(module: WasmModule) {
        output.write(WASM_MAGIC)
        output.write(WASM_VERSION)
        writeTypesSection(module)
        writeImportsSection(module)
        writeFunctionsSection(module)
        writeTableSection(module)
        writeMemorySection(module)
        writeGlobalsSection(module)
        writeExportSection(module)
        writeStartSection(module)
        writeElementSection(module)
        writeCodeSection(module)
        writeDataSection(module)

        if (objectFile) {
            writeLinkingSection(module)
            writeRelocationSections(module)
        }
    }

    private fun writeTypesSection(module: WasmModule) {
        if (module.types.size > 0) {
            writeSection(SectionType.TYPE) {
                writeTypesSectionContents(module.types)
            }
        }
    }

    private fun writeTypesSectionContents(types: List<WasmFuncType>) {
        output.writeVecSize(types.size)
        for (type in types) {
            writeFuncType(type)
        }
    }

    private fun writeImportsSection(module: WasmModule) {
        var imports = module.imports

        if (objectFile) {
            if (module.memoryPageCount != null) {
                val memoryImport = Wasm.importMemory(
                    moduleName = "env",
                    entityName = "__linear_memory",
                    identifier = "memory",
                    limits = WasmLimits(module.memoryPageCount, null),
                )
                imports = imports + listOf(memoryImport)
            }

            if (module.table.size > 0) {
                val tableImport = Wasm.importTable(
                    moduleName = "env",
                    entityName = "__indirect_function_table",
                    identifier = "indirect_function_table",
                    limits = WasmLimits(module.table.size, null),
                )
                imports = imports + listOf(tableImport)
            }
        }

        if (imports.size > 0) {
            writeSection(SectionType.IMPORT) {
                writeImportsSectionContents(imports)
            }
        }
    }

    private fun writeImportsSectionContents(imports: List<WasmImport>) {
        output.writeVecSize(imports.size)
        for (import in imports) {
            writeImport(import)
        }
    }

    private fun writeFunctionsSection(module: WasmModule) {
        if (module.functions.size > 0) {
            writeSection(SectionType.FUNCTION) {
                writeFunctionsSectionContents(module.functions)
            }
        }
    }

    private fun writeFunctionsSectionContents(functions: List<WasmFunction>) {
        output.writeVecSize(functions.size)
        for (function in functions) {
            writeTypeIndex(function.type())
        }
    }

    private fun writeTableSection(module: WasmModule) {
        if (!objectFile && module.table.size > 0) {
            writeSection(SectionType.TABLE) {
                writeTableSectionContents(module.table)
            }
        }
    }

    private fun writeTableSectionContents(table: List<String>) {
        output.writeVecSize(1)
        writeReferenceTypeFunction()
        writeLimits(table.size, table.size)
    }

    private fun writeMemorySection(module: WasmModule) {
        if (!objectFile && module.memoryPageCount != null) {
            writeSection(SectionType.MEMORY) {
                writeMemorySectionContents(module.memoryPageCount)
            }
        }
    }

    private fun writeMemorySectionContents(memoryPageCount: Int) {
        output.writeVecSize(1)
        writeLimits(memoryPageCount)
    }

    private fun writeGlobalsSection(module: WasmModule) {
        if (module.globals.size > 0) {
            writeSection(SectionType.GLOBAL) {
                writeGlobalsSectionContents(module.globals)
            }
        }
    }

    private fun writeGlobalsSectionContents(globals: List<WasmGlobal>) {
        output.writeVecSize(globals.size)
        for (global in globals) {
            writeGlobal(global)
        }
    }

    private fun writeGlobal(global: WasmGlobal) {
        writeValueType(global.type)
        output.write8(if (global.mutable) 0x01 else 0x00)
        writeExpression(listOf(global.value))
    }

    private fun writeExportSection(module: WasmModule) {
        val exportedFunctions = module.functions.filter { function -> function.export }
        val exports = exportedFunctions.map { function ->
            WasmExport(
                function.identifier,
                WasmExportDescriptor.Function(symbolTable.functionIndex(function.identifier)),
            )
        }.toMutableList()
        if (!objectFile && module.memoryPageCount != null) {
            exports.add(WasmExport("memory", WasmExportDescriptor.Memory(0)))
        }
        if (exports.size > 0) {
            writeSection(SectionType.EXPORT) {
                writeExportSectionContents(exports)
            }
        }
    }

    private fun writeExportSectionContents(exports: List<WasmExport>) {
        output.writeVecSize(exports.size)
        for (export in exports) {
            output.writeString(export.name)
            when (export.descriptor) {
                is WasmExportDescriptor.Function -> {
                    output.write8(0x00)
                    output.writeUnsignedLeb128(export.descriptor.funcIndex)
                }
                is WasmExportDescriptor.Memory -> {
                    output.write8(0x02)
                    output.writeUnsignedLeb128(export.descriptor.memoryIndex)
                }
            }
        }
    }

    private fun writeStartSection(module: WasmModule) {
        if (!objectFile && module.start != null) {
            writeSection(SectionType.START) {
                writeFuncIndex(module.start)
            }
        }
    }

    private fun writeElementSection(module: WasmModule) {
        if (module.table.size > 0) {
            writeSection(SectionType.ELEMENT) {
                writeElementSectionContents(module.table)
            }
        }
    }

    private fun writeElementSectionContents(table: List<String>) {
        output.writeVecSize(1)
        output.write8(0x00)
        writeExpression(listOf(Wasm.I.i32Const(0)))
        output.writeVecSize(table.size)
        for (name in table) {
            writeFuncIndex(name)
        }
    }

    private fun writeCodeSection(module: WasmModule) {
        if (module.functions.size > 0) {
            writeSection(SectionType.CODE) {
                writeCodeSectionContents(module.functions)
            }
        }
    }

    private fun writeCodeSectionContents(functions: List<WasmFunction>) {
        output.writeVecSize(functions.size)
        for (function in functions) {
            writeFunction(function)
        }
    }

    private fun writeFunction(function: WasmFunction) {
        writeWithSizePrefix {
            writeFunctionCode(function)
        }
    }

    private fun writeFunctionCode(function: WasmFunction) {
        localIndices = mutableMapOf()
        for (param in function.params) {
            addLocalIndex(param.identifier)
        }
        output.writeVecSize(function.locals.size)
        for (local in function.locals) {
            output.writeUnsignedLeb128(1)
            writeValueType(local.type)
            addLocalIndex(local.identifier)
        }
        writeExpression(function.body)
    }

    private fun writeDataSection(module: WasmModule) {
        if (module.dataSegments.size > 0) {
            writeSection(SectionType.DATA) {
                writeDataSectionContents(module.dataSegments)
            }
        }
    }

    private fun writeDataSectionContents(dataSegments: List<WasmDataSegment>) {
        output.writeVecSize(dataSegments.size)
        for (dataSegment in dataSegments) {
            writeDataSegment(dataSegment)
        }
    }

    private fun writeDataSegment(dataSegment: WasmDataSegment) {
        output.write8(0x00)
        writeExpression(listOf(Wasm.I.i32Const(dataSegment.offset)))
        output.writeVecSize(dataSegment.size)
        output.write(dataSegment.bytes ?: ByteArray(dataSegment.size))
    }

    private fun writeImport(import: WasmImport) {
        output.writeString(import.moduleName)
        output.writeString(import.entityName)
        when (import.descriptor) {
            is WasmImportDescriptor.Function -> {
                writeImportDescriptorFunction(import.descriptor)
            }
            is WasmImportDescriptor.Memory -> {
                writeImportDescriptorMemory(import.descriptor)
            }
            is WasmImportDescriptor.Table -> {
                writeImportDescriptorTable(import.descriptor)
            }
        }
    }

    private fun writeImportDescriptorFunction(descriptor: WasmImportDescriptor.Function) {
        output.write8(0x00)
        writeTypeIndex(descriptor.type())
    }

    private fun writeImportDescriptorMemory(descriptor: WasmImportDescriptor.Memory) {
        output.write8(0x02)
        writeLimits(descriptor.limits)
    }

    private fun writeImportDescriptorTable(descriptor: WasmImportDescriptor.Table) {
        output.write8(0x01)
        writeReferenceTypeFunction()
        writeLimits(descriptor.limits)
    }

    private fun writeLinkingSection(module: WasmModule) {
        writeSection(SectionType.CUSTOM) {
            output.writeString("linking")
            output.writeUnsignedLeb128(2) // Version

            if (module.dataSegments.size > 0) {
                output.write8(LinkingSubsectionType.WASM_SEGMENT_INFO.id)
                writeWithSizePrefix {
                    writeSegmentInfoContents(module)
                }
            }

            output.write8(LinkingSubsectionType.WASM_SYMBOL_TABLE.id)
            writeWithSizePrefix {
                writeSymbolTableContents(module)
            }

            if (module.start != null) {
                output.write8(LinkingSubsectionType.WASM_INIT_FUNCS.id)
                writeWithSizePrefix {
                    output.writeVecSize(1)
                    val funcIndex = symbolTable.functionIndex(module.start)
                    val symbolIndex = symbolTable.functionIndexToSymbolIndex(funcIndex)
                    output.writeUnsignedLeb128(0)
                    output.writeUnsignedLeb128(symbolIndex)
                }
            }
        }
    }

    private fun writeSegmentInfoContents(module: WasmModule) {
        output.writeVecSize(module.dataSegments.size)
        module.dataSegments.forEachIndexed { dataSegmentIndex, dataSegment ->
            output.writeString("DATA_SEGMENT_$dataSegmentIndex")
            writeMemoryAlignment(1)
            output.write8(0) // flags
        }
    }

    private fun writeSymbolTableContents(module: WasmModule) {
        val symbolInfos = symbolTable.symbolInfos()

        output.writeVecSize(symbolInfos.size)
        for (symbolInfo in symbolInfos) {
            writeSymbolInfo(symbolInfo)
        }
    }

    private fun writeSymbolInfo(info: WasmSymbolInfo) {
        when (info) {
            is WasmSymbolInfo.Data ->
                writeDataSymbolInfo(info)
            is WasmSymbolInfo.Function ->
                writeFunctionSymbolInfo(info)
            is WasmSymbolInfo.Global ->
                writeGlobalSymbolInfo(info)
        }
    }

    private fun writeDataSymbolInfo(info: WasmSymbolInfo.Data) {
        output.write8(SymbolType.DATA.id)
        output.writeUnsignedLeb128(info.flags)
        output.writeString(info.identifier)
        output.writeUnsignedLeb128(info.dataSegmentIndex)
        output.writeUnsignedLeb128(info.offset)
        output.writeUnsignedLeb128(info.size)
    }

    private fun writeFunctionSymbolInfo(info: WasmSymbolInfo.Function) {
        output.write8(SymbolType.FUNCTION.id)
        output.writeUnsignedLeb128(info.flags)
        writeFuncIndex(info.identifier)
        output.writeString(info.identifier)
    }

    private fun writeGlobalSymbolInfo(info: WasmSymbolInfo.Global) {
        output.write8(SymbolType.GLOBAL.id)
        output.writeUnsignedLeb128(info.flags)
        writeGlobalIndex(info.identifier)
        output.writeString(info.identifier)
    }

    private fun writeRelocationSections(module: WasmModule) {
        val relocationsBySectionIndex = relocationEntries.groupBy { relocation -> relocation.sectionIndex }
        for ((sectionIndex, relocationsInSection) in relocationsBySectionIndex) {
            writeRelocationSection(sectionIndex, relocationsInSection)
        }
    }

    private fun writeRelocationSection(sectionIndex: Int, relocationEntries: List<RelocationEntry>) {
        writeSection(SectionType.CUSTOM) {
            output.writeString("reloc.CODE") // TODO: don't assume CODE
            output.writeUnsignedLeb128(sectionIndex)
            output.writeVecSize(relocationEntries.size)
            for (relocationEntry in relocationEntries) {
                writeRelocationEntry(relocationEntry)
            }
        }
    }

    private fun writeRelocationEntry(relocationEntry: RelocationEntry) {
        output.write8(relocationEntry.type.id)
        output.writeUnsignedLeb128(relocationEntry.offset)
        output.writeUnsignedLeb128(relocationEntry.index)
        if (relocationEntry.addend != null) {
            output.writeSignedLeb128(relocationEntry.addend)
        }
    }

    private fun writeLimits(min: Int) {
        output.write8(0x00)
        output.writeUnsignedLeb128(min)
    }

    private fun writeLimits(min: Int, max: Int) {
        output.write8(0x01)
        output.writeUnsignedLeb128(min)
        output.writeUnsignedLeb128(max)
    }

    private fun writeLimits(limits: WasmLimits) {
        if (limits.max == null) {
            writeLimits(limits.min)
        } else {
            writeLimits(limits.min, limits.max)
        }
    }

    private fun writeFuncIndex(name: String) {
        val funcIndex = symbolTable.functionIndex(name)
        writeRelocatableIndex(
            relocationType = RelocationType.FUNCTION_INDEX_LEB,
            index = funcIndex,
            symbolIndex = symbolTable.functionIndexToSymbolIndex(funcIndex),
        )
    }

    private fun writeGlobalIndex(name: String) {
        val globalIndex = symbolTable.globalIndex(name)
        writeRelocatableIndex(
            relocationType = RelocationType.GLOBAL_INDEX_LEB,
            index = globalIndex,
            symbolIndex = symbolTable.globalIndexToSymbolIndex(globalIndex),
        )
    }

    private fun writeLabelIndex(name: String) {
        output.writeUnsignedLeb128(labelIndex(name))
    }

    private fun labelIndex(name: String): Int {
        val index = labelStack.indexOf(name)
        if (index == -1) {
            throw CompilerError("could not find label", NullSource)
        } else {
            return labelStack.size - 1 - index
        }
    }

    private fun addLocalIndex(name: String) {
        localIndices[name] = localIndices.size
    }

    private fun writeLocalIndex(name: String) {
        output.writeUnsignedLeb128(localIndex(name))
    }

    private fun localIndex(name: String): Int {
        return localIndices.getValue(name)
    }

    private fun writeTableIndex(tableIndex: Int) {
        output.writeUnsignedLeb128(tableIndex)
    }

    private fun addTypeIndex(funcType: WasmFuncType) {
        typeIndices.add(funcType, typeIndices.size)
    }

    private fun writeTypeIndex(funcType: WasmFuncType) {
        val typeIndex = typeIndex(funcType)
        writeRelocatableIndex(
            relocationType = RelocationType.TYPE_INDEX_LEB,
            index = typeIndex,
            symbolIndex = typeIndex,
        )
    }

    private fun typeIndex(funcType: WasmFuncType): Int {
        return typeIndices.getValue(funcType)
    }

    private fun writeRelocatableIndex(relocationType: RelocationType, index: Int, symbolIndex: Int, addend: Int? = null) {
        if (objectFile && currentSectionType == SectionType.CODE) {
            relocationEntries.add(RelocationEntry(
                sectionIndex = currentSectionIndex,
                type = relocationType,
                offset = currentSectionOffset(),
                index = symbolIndex,
                addend = addend,
            ))
            if (relocationType.isSigned) {
                output.writePaddedSignedLeb128(index)
            } else {
                output.writePaddedUnsignedLeb128(index)
            }
        } else {
            if (relocationType.isSigned) {
                output.writeSignedLeb128(index)
            } else {
                output.writeUnsignedLeb128(index)
            }
        }
    }

    private fun writeFuncType(type: WasmFuncType) {
        addTypeIndex(type)
        output.write8(0x60)
        writeResultType(type.params)
        writeResultType(type.results)
    }

    private fun writeResultType(types: List<WasmValueType>) {
        output.writeVecSize(types.size)
        for (type in types) {
            writeValueType(type)
        }
    }

    private fun writeValueType(type: WasmValueType) {
        val binaryEncoding = when (type) {
            WasmValueType.i32 -> 0x7F
        }.toByte()
        output.write8(binaryEncoding)
    }

    private fun writeBlockType(results: List<WasmValueType>) {
        if (results.size == 0) {
            output.write8(0x40)
        } else if (results.size == 1) {
            writeValueType(results[0])
        } else {
            // TODO: Generate type when building module
            throw CompilerError("blocktype of multiple types not supported", NullSource)
        }
    }

    private fun writeReferenceTypeFunction() {
        output.write8(0x70)
    }

    private fun writeSection(sectionType: SectionType, writeContents: () -> Unit) {
        currentSectionIndex++
        currentSectionType = sectionType
        output.write8(sectionType.id)
        val writeSize = output.writeSizePlaceholder()
        currentSectionStartPosition = output.position
        writeContents()
        writeSize()
    }

    private fun currentSectionOffset(): Int {
        return output.position - currentSectionStartPosition
    }

    private fun writeExpression(instructions: List<WasmInstruction>) {
        for (instruction in instructions) {
            writeInstruction(instruction)
        }
        output.write8(0x0B) // end
    }

    private fun writeInstruction(instruction: WasmInstruction) {
        when (instruction) {
            is WasmInstruction.Branch -> {
                output.write8(0x0C)
                writeLabelIndex(instruction.identifier)
            }
            is WasmInstruction.Call -> {
                output.write8(0x10)
                writeFuncIndex(instruction.identifier)
            }
            is WasmInstruction.CallIndirect -> {
                output.write8(0x11)
                writeTypeIndex(instruction.type)
                writeTableIndex(0)
            }
            WasmInstruction.Drop -> {
                output.write8(0x1A)
            }
            WasmInstruction.Else -> {
                output.write8(0x05)
            }
            WasmInstruction.End -> {
                output.write8(0x0B)
                labelStack.removeAt(labelStack.lastIndex)
            }
            is WasmInstruction.GlobalSet -> {
                output.write8(0x24)
                writeGlobalIndex(instruction.identifier)
            }
            WasmInstruction.I32Add -> {
                output.write8(0x6A)
            }
            WasmInstruction.I32And -> {
                output.write8(0x71)
            }
            WasmInstruction.I32DivideUnsigned -> {
                output.write8(0x6E)
            }
            WasmInstruction.I32Equals -> {
                output.write8(0x46)
            }
            WasmInstruction.I32GreaterThanSigned -> {
                output.write8(0x4A)
            }
            WasmInstruction.I32GreaterThanUnsigned -> {
                output.write8(0x4B)
            }
            WasmInstruction.I32GreaterThanOrEqualSigned -> {
                output.write8(0x4E)
            }
            WasmInstruction.I32GreaterThanOrEqualUnsigned -> {
                output.write8(0x4F)
            }
            WasmInstruction.I32LessThanSigned -> {
                output.write8(0x48)
            }
            WasmInstruction.I32LessThanUnsigned -> {
                output.write8(0x49)
            }
            WasmInstruction.I32LessThanOrEqualSigned -> {
                output.write8(0x4C)
            }
            WasmInstruction.I32LessThanOrEqualUnsigned -> {
                output.write8(0x4D)
            }
            is WasmInstruction.I32Load -> {
                output.write8(0x28)
                writeMemArg(alignment = instruction.alignment
                    ?: 4, offset = instruction.offset)
            }
            WasmInstruction.I32Load8Unsigned -> {
                output.write8(0x2D)
                writeMemArg(alignment = 1, offset = 0)
            }
            WasmInstruction.I32Multiply -> {
                output.write8(0x6C)
            }
            WasmInstruction.I32NotEqual -> {
                output.write8(0x47)
            }
            is WasmInstruction.I32Store -> {
                output.write8(0x36)
                writeMemArg(alignment = instruction.alignment
                    ?: 4, offset = instruction.offset)
            }
            WasmInstruction.I32Store8 -> {
                output.write8(0x3A)
                writeMemArg(alignment = 1, offset = 0)

            }
            WasmInstruction.I32Sub -> {
                output.write8(0x6B)
            }
            is WasmInstruction.If -> {
                output.write8(0x04)
                writeBlockType(instruction.results)
                labelStack.add(null)
            }
            is WasmInstruction.LocalSet -> {
                output.write8(0x21)
                writeLocalIndex(instruction.identifier)
            }
            is WasmInstruction.Loop -> {
                output.write8(0x03)
                writeBlockType(instruction.results)
                labelStack.add(instruction.identifier)
            }
            WasmInstruction.MemoryGrow -> {
                output.write8(0x40)
                output.write8(0x00)
            }
            is WasmInstruction.Folded.GlobalGet -> {
                output.write8(0x23)
                writeGlobalIndex(instruction.identifier)
            }
            is WasmInstruction.Folded.I32Const -> {
                output.write8(0x41)
                when (instruction.value) {
                    is WasmConstValue.I32 ->
                        output.writeSignedLeb128(instruction.value.value)

                    is WasmConstValue.DataIndex -> {
                        writeRelocatableIndex(
                            RelocationType.MEMORY_ADDR_SLEB,
                            index = symbolTable.dataAddress(instruction.value.key),
                            symbolIndex = symbolTable.dataSymbolIndex(instruction.value.key),
                            addend = 0,
                        )
                    }

                    is WasmConstValue.LateIndex ->
                        output.writeSignedLeb128(lateIndices[instruction.value.ref]!!)

                    is WasmConstValue.TableEntryIndex -> {
                        val functionIndex = symbolTable.functionIndex(instruction.value.identifier)
                        val tableEntryIndex = symbolTable.tableEntryIndex(instruction.value.identifier)
                        writeRelocatableIndex(
                            RelocationType.TABLE_INDEX_SLEB,
                            index = tableEntryIndex,
                            symbolIndex = symbolTable.functionIndexToSymbolIndex(functionIndex),
                        )
                    }
                }
            }
            is WasmInstruction.Folded.LocalGet -> {
                output.write8(0x20)
                writeLocalIndex(instruction.identifier)
            }
            WasmInstruction.Folded.MemorySize -> {
                output.write8(0x3F)
                output.write8(0x00)
            }
            is WasmInstruction.Unfoldable -> {
                for (unfoldedInstruction in instruction.unfold()) {
                    writeInstruction(unfoldedInstruction)
                }
            }
            else -> {
                throw CompilerError("unhandled instruction: $instruction", NullSource)
            }
        }
    }

    private fun writeMemArg(alignment: Int, offset: Int) {
        writeMemoryAlignment(alignment)
        output.writeUnsignedLeb128(offset)
    }

    private fun writeMemoryAlignment(alignment: Int) {
        val alignmentEncoding = when (alignment) {
            1 -> 0
            2 -> 1
            4 -> 2
            8 -> 3
            else -> throw CompilerError("unexpected alignment $alignment", NullSource)
        }
        output.writeUnsignedLeb128(alignmentEncoding)
    }

    private fun writeWithSizePrefix(writeContents: () -> Unit) {
        val writeSize = output.writeSizePlaceholder()
        writeContents()
        writeSize()
    }
}

@ExperimentalUnsignedTypes
private class BufferWriter {
    private var buffer = ByteBuffer.allocate(1024)

    val position: Int
        get() = buffer.position()

    fun writeTo(output: OutputStream) {
        buffer.limit(buffer.position())
        buffer.position(0)
        Channels.newChannel(output).write(buffer)
        buffer.limit(buffer.capacity())
    }

    fun write(bytes: ByteArray, offset: Int, length: Int) {
        val minCapacity = buffer.position() + length - offset
        growTo(minCapacity)
        buffer.put(bytes, offset, length)
    }

    fun write(bytes: UByteArray) {
        write(bytes.toByteArray())
    }

    fun write(bytes: ByteArray) {
        write(bytes, offset = 0, length = bytes.size)
    }

    private fun growTo(minCapacity: Int) {
        if (minCapacity > buffer.capacity()) {
            var newCapacity = buffer.capacity() * 2
            while (minCapacity > newCapacity) {
                newCapacity *= 2
            }
            val newBuffer = ByteBuffer.allocate(newCapacity)
            buffer.flip()
            newBuffer.put(buffer)
            buffer = newBuffer
        }
    }

    fun write8(byte: Byte) {
        write(byteArrayOf(byte))
    }

    fun writeVecSize(size: Int) {
        writeUnsignedLeb128(size)
    }

    fun writeUnsignedLeb128(value: Int) {
        write(Leb128Encoding.encodeUnsignedInt32(value))
    }

    fun writePaddedUnsignedLeb128(value: Int) {
        write(Leb128Encoding.encodePaddedUnsignedInt32(value))
    }

    fun writeSignedLeb128(value: Int) {
        write(Leb128Encoding.encodeSignedInt32(value))
    }

    fun writePaddedSignedLeb128(value: Int) {
        write(Leb128Encoding.encodePaddedSignedInt32(value))
    }

    fun writeSizePlaceholder(): () -> Unit {
        val sizePosition = buffer.position()
        write(Leb128Encoding.encodePaddedUnsignedInt32(0))
        val contentPosition = buffer.position()
        return {
            val currentPosition = buffer.position()
            val size = currentPosition - contentPosition
            buffer.position(sizePosition)
            write(Leb128Encoding.encodePaddedUnsignedInt32(size))
            buffer.position(currentPosition)
        }
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeUnsignedLeb128(bytes.size)
        write(bytes)
    }
}

private class WasmExport(
    val name: String,
    val descriptor: WasmExportDescriptor,
)

private sealed class WasmExportDescriptor {
    class Function(val funcIndex: Int) : WasmExportDescriptor()
    class Memory(val memoryIndex: Int) : WasmExportDescriptor()
}
