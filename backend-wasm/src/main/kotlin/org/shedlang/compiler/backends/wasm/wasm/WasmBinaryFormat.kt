package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.NullSource
import org.shedlang.compiler.backends.wasm.LateIndex
import org.shedlang.compiler.backends.wasm.add
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

internal object WasmBinaryFormat {
    internal fun write(module: WasmModule, output: OutputStream, lateIndices: Map<LateIndex, Int>) {
        val writer = WasmBinaryFormatWriter(outputStream = output, lateIndices = lateIndices, symbolTable = WasmSymbolTable.forModule(module))
        writer.write(module)
    }
}

@ExperimentalUnsignedTypes
private class WasmBinaryFormatWriter(
    private val lateIndices: Map<LateIndex, Int>,
    private val symbolTable: WasmSymbolTable,
    private val outputStream: OutputStream,
) {
    private val WASM_MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
    private val WASM_VERSION = byteArrayOf(0x01, 0x00, 0x00, 0x00)
    private val globalIndices = mutableMapOf<String, Int>()
    private val labelStack = mutableListOf<String?>()
    private var localIndices = mutableMapOf<String, Int>()
    private val typeIndices = mutableMapOf<WasmFuncType, Int>()


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

    internal fun write(module: WasmModule) {
        outputStream.write(WASM_MAGIC)
        outputStream.write(WASM_VERSION)
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
    }

    private fun writeTypesSection(module: WasmModule) {
        if (module.types.size > 0) {
            writeSection(SectionType.TYPE) { output ->
                writeTypesSectionContents(module.types, output)
            }
        }
    }

    private fun writeTypesSectionContents(types: List<WasmFuncType>, output: BufferWriter) {
        output.writeVecSize(types.size)
        for (type in types) {
            writeFuncType(type, output)
        }
    }

    private fun writeImportsSection(module: WasmModule) {
        if (module.imports.size > 0) {
            writeSection(SectionType.IMPORT) { output ->
                writeImportsSectionContents(module.imports, output)
            }
        }
    }

    private fun writeImportsSectionContents(imports: List<WasmImport>, output: BufferWriter) {
        output.writeVecSize(imports.size)
        for (import in imports) {
            writeImport(import, output)
        }
    }

    private fun writeFunctionsSection(module: WasmModule) {
        if (module.functions.size > 0) {
            writeSection(SectionType.FUNCTION) { output ->
                writeFunctionsSectionContents(module.functions, output)
            }
        }
    }

    private fun writeFunctionsSectionContents(functions: List<WasmFunction>, output: BufferWriter) {
        output.writeVecSize(functions.size)
        for (function in functions) {
            writeTypeIndex(function.type(), output)
        }
    }

    private fun writeTableSection(module: WasmModule) {
        if (module.table.size > 0) {
            writeSection(SectionType.TABLE) { output ->
                writeTableSectionContents(module.table, output)
            }
        }
    }

    private fun writeTableSectionContents(table: List<String>, output: BufferWriter) {
        output.writeVecSize(1)
        // reftype
        output.write8(0x70) // funcref
        // limits
        writeLimits(table.size, table.size, output)
    }

    private fun writeMemorySection(module: WasmModule) {
        if (module.memoryPageCount != null) {
            writeSection(SectionType.MEMORY) { output ->
                writeMemorySectionContents(module.memoryPageCount, output)
            }
        }
    }

    private fun writeMemorySectionContents(memoryPageCount: Int, output: BufferWriter) {
        output.writeVecSize(1)
        writeLimits(memoryPageCount, output)
    }

    private fun writeGlobalsSection(module: WasmModule) {
        if (module.globals.size > 0) {
            writeSection(SectionType.GLOBAL) { output ->
                writeGlobalsSectionContents(module.globals, output)
            }
        }
    }

    private fun writeGlobalsSectionContents(globals: List<WasmGlobal>, output: BufferWriter) {
        output.writeVecSize(globals.size)
        for (global in globals) {
            writeGlobal(global, output)
            addGlobalIndex(global.identifier)
        }
    }

    private fun writeGlobal(global: WasmGlobal, output: BufferWriter) {
        writeValueType(global.type, output)
        output.write8(if (global.mutable) 0x01 else 0x00)
        writeExpression(listOf(global.value), output)
    }

    private fun writeExportSection(module: WasmModule) {
        val exportedFunctions = module.functions.filter { function -> function.exportName != null }
        val exports = exportedFunctions.map { function ->
            WasmExport(
                function.exportName!!,
                WasmExportDescriptor.Function(symbolTable.funcIndex(function.identifier)),
            )
        }.toMutableList()
        if (module.memoryPageCount != null) {
            exports.add(WasmExport("memory", WasmExportDescriptor.Memory(0)))
        }
        if (exports.size > 0) {
            writeSection(SectionType.EXPORT) { output ->
                writeExportSectionContents(exports, output)
            }
        }
    }

    private fun writeExportSectionContents(exports: List<WasmExport>, output: BufferWriter) {
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
        if (module.start != null) {
            writeSection(SectionType.START) { output ->
                writeFuncIndex(module.start, output)
            }
        }
    }

    private fun writeElementSection(module: WasmModule) {
        if (module.table.size > 0) {
            writeSection(SectionType.ELEMENT) { output ->
                writeElementSectionContents(module.table, output)
            }
        }
    }

    private fun writeElementSectionContents(table: List<String>, output: BufferWriter) {
        output.writeVecSize(1)
        output.write8(0x00)
        writeExpression(listOf(Wasm.I.i32Const(0)), output)
        output.writeVecSize(table.size)
        for (name in table) {
            writeFuncIndex(name, output)
        }
    }

    private fun writeCodeSection(module: WasmModule) {
        if (module.functions.size > 0) {
            writeSection(SectionType.CODE) { output ->
                writeCodeSectionContents(module.functions, output)
            }
        }
    }

    private fun writeCodeSectionContents(functions: List<WasmFunction>, output: BufferWriter) {
        output.writeVecSize(functions.size)
        for (function in functions) {
            writeFunction(function, output)
        }
    }

    private fun writeFunction(function: WasmFunction, output: BufferWriter) {
        val functionCodeOutput = BufferWriter()
        writeFunctionCode(function, functionCodeOutput)
        output.writeUnsignedLeb128(functionCodeOutput.size)
        functionCodeOutput.writeTo(output)
    }

    private fun writeFunctionCode(function: WasmFunction, output: BufferWriter) {
        localIndices = mutableMapOf()
        for (param in function.params) {
            addLocalIndex(param.identifier)
        }
        output.writeVecSize(function.locals.size)
        for (local in function.locals) {
            output.writeUnsignedLeb128(1)
            writeValueType(local.type, output)
            addLocalIndex(local.identifier)
        }
        writeExpression(function.body, output)
    }

    private fun writeDataSection(module: WasmModule) {
        if (module.dataSegments.size > 0) {
            writeSection(SectionType.DATA) { output ->
                writeDataSectionContents(module.dataSegments, output)
            }
        }
    }

    private fun writeDataSectionContents(dataSegments: List<WasmDataSegment>, output: BufferWriter) {
        output.writeVecSize(dataSegments.size)
        for (dataSegment in dataSegments) {
            writeDataSegment(dataSegment, output)
        }
    }

    private fun writeDataSegment(dataSegment: WasmDataSegment, output: BufferWriter) {
        output.write8(0x00)
        writeExpression(listOf(Wasm.I.i32Const(dataSegment.offset)), output)
        output.writeVecSize(dataSegment.bytes.size)
        output.write(dataSegment.bytes)
    }

    private fun writeImport(import: WasmImport, output: BufferWriter) {
        output.writeString(import.moduleName)
        output.writeString(import.entityName)
        when (import.descriptor) {
            is WasmImportDescriptor.Function -> {
                writeImportDescriptionFunction(import.descriptor, output)
            }
        }
    }

    private fun writeImportDescriptionFunction(descriptor: WasmImportDescriptor.Function, output: BufferWriter) {
        output.write8(0x00)
        writeTypeIndex(descriptor.type(), output)
    }

    private fun writeLimits(min: Int, output: BufferWriter) {
        output.write8(0x00)
        output.writeUnsignedLeb128(min)
    }

    private fun writeLimits(min: Int, max: Int, output: BufferWriter) {
        output.write8(0x01)
        output.writeUnsignedLeb128(min)
        output.writeUnsignedLeb128(max)
    }

    private fun writeFuncIndex(name: String, output: BufferWriter) {
        output.writeUnsignedLeb128(symbolTable.funcIndex(name))
    }

    private fun addGlobalIndex(name: String) {
        globalIndices.add(name, globalIndices.size)
    }

    private fun writeGlobalIndex(name: String, output: BufferWriter) {
        output.writeUnsignedLeb128(globalIndex(name))
    }

    private fun globalIndex(name: String): Int {
        return globalIndices.getValue(name)
    }

    private fun writeLabelIndex(name: String, output: BufferWriter) {
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

    private fun writeLocalIndex(name: String, output: BufferWriter) {
        output.writeUnsignedLeb128(localIndex(name))
    }

    private fun localIndex(name: String): Int {
        return localIndices.getValue(name)
    }

    private fun writeTableIndex(tableIndex: Int, output: BufferWriter) {
        output.writeUnsignedLeb128(tableIndex)
    }

    private fun addTypeIndex(funcType: WasmFuncType) {
        typeIndices.add(funcType, typeIndices.size)
    }

    private fun writeTypeIndex(funcType: WasmFuncType, output: BufferWriter) {
        output.writeUnsignedLeb128(typeIndex(funcType))
    }

    private fun typeIndex(funcType: WasmFuncType): Int {
        return typeIndices.getValue(funcType)
    }

    private fun writeFuncType(type: WasmFuncType, output: BufferWriter) {
        addTypeIndex(type)
        output.write8(0x60)
        writeResultType(type.params, output)
        writeResultType(type.results, output)
    }

    private fun writeResultType(types: List<WasmValueType>, output: BufferWriter) {
        output.writeVecSize(types.size)
        for (type in types) {
            writeValueType(type, output)
        }
    }

    private fun writeValueType(type: WasmValueType, output: BufferWriter) {
        val binaryEncoding = when (type) {
            WasmValueType.i32 -> 0x7F
        }.toByte()
        output.write8(binaryEncoding)
    }

    private fun writeBlockType(results: List<WasmValueType>, output: BufferWriter) {
        if (results.size == 0) {
            output.write8(0x40)
        } else if (results.size == 1) {
            writeValueType(results[0], output)
        } else {
            // TODO: Generate type when building module
            throw CompilerError("blocktype of multiple types not supported", NullSource)
        }
    }

    private fun writeSection(sectionType: SectionType, writeContents: (BufferWriter) -> Unit) {
        outputStream.write8(sectionType.id)
        val bufferWriter = BufferWriter()
        writeContents(bufferWriter)
        outputStream.writeUnsignedLeb128(bufferWriter.size)
        bufferWriter.writeTo(outputStream)
    }

    private fun writeExpression(instructions: List<WasmInstruction>, output: BufferWriter) {
        for (instruction in instructions) {
            writeInstruction(instruction, output)
        }
        output.write8(0x0B) // end
    }

    private fun writeInstruction(instruction: WasmInstruction, output: BufferWriter) {
        when (instruction) {
            is WasmInstruction.Branch -> {
                output.write8(0x0C)
                writeLabelIndex(instruction.identifier, output)
            }
            is WasmInstruction.Call -> {
                output.write8(0x10)
                writeFuncIndex(instruction.identifier, output)
            }
            is WasmInstruction.CallIndirect -> {
                output.write8(0x11)
                writeTypeIndex(instruction.type, output)
                writeTableIndex(0, output)
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
                writeGlobalIndex(instruction.identifier, output)
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
                    ?: 4, offset = instruction.offset, output)
            }
            WasmInstruction.I32Load8Unsigned -> {
                output.write8(0x2D)
                writeMemArg(alignment = 1, offset = 0, output)
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
                    ?: 4, offset = instruction.offset, output)
            }
            WasmInstruction.I32Store8 -> {
                output.write8(0x3A)
                writeMemArg(alignment = 1, offset = 0, output)

            }
            WasmInstruction.I32Sub -> {
                output.write8(0x6B)
            }
            is WasmInstruction.If -> {
                output.write8(0x04)
                writeBlockType(instruction.results, output)
                labelStack.add(null)
            }
            is WasmInstruction.LocalSet -> {
                output.write8(0x21)
                writeLocalIndex(instruction.identifier, output)
            }
            is WasmInstruction.Loop -> {
                output.write8(0x03)
                writeBlockType(instruction.results, output)
                labelStack.add(instruction.identifier)
            }
            WasmInstruction.MemoryGrow -> {
                output.write8(0x40)
                output.write8(0x00)
            }
            is WasmInstruction.Folded.GlobalGet -> {
                output.write8(0x23)
                writeGlobalIndex(instruction.identifier, output)
            }
            is WasmInstruction.Folded.I32Const -> {
                output.write8(0x41)
                output.writeSignedLeb128(constValueToInt(instruction.value))
            }
            is WasmInstruction.Folded.LocalGet -> {
                output.write8(0x20)
                writeLocalIndex(instruction.identifier, output)
            }
            WasmInstruction.Folded.MemorySize -> {
                output.write8(0x3F)
                output.write8(0x00)
            }
            is WasmInstruction.Unfoldable -> {
                for (unfoldedInstruction in instruction.unfold()) {
                    writeInstruction(unfoldedInstruction, output)
                }
            }
            else -> {
                throw CompilerError("unhandled instruction: $instruction", NullSource)
            }
        }
    }

    private fun writeMemArg(alignment: Int, offset: Int, output: BufferWriter) {
        val alignmentEncoding = when (alignment) {
            1 -> 0
            2 -> 1
            4 -> 2
            8 -> 3
            else -> throw CompilerError("unexpected alignment $alignment", NullSource)
        }
        output.writeUnsignedLeb128(alignmentEncoding)
        output.writeUnsignedLeb128(offset)
    }

    private fun constValueToInt(value: WasmConstValue): Int {
        return when (value) {
            is WasmConstValue.I32 -> value.value
            is WasmConstValue.LateIndex -> lateIndices[value.ref]!!
            is WasmConstValue.TableEntryIndex -> symbolTable.tableEntryIndex(value.identifier)
        }
    }
}


private fun OutputStream.write8(byte: Byte) {
    write(byte.toInt())
}

@ExperimentalUnsignedTypes
private fun OutputStream.writeUnsignedLeb128(value: Int) {
    write(Leb128Encoding.encodeUnsignedInt32(value).toByteArray())
}

@ExperimentalUnsignedTypes
private class BufferWriter {
    private var buffer = ByteBuffer.allocate(1024)

    val size: Int
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

    fun writeSignedLeb128(value: Int) {
        write(Leb128Encoding.encodeSignedInt32(value))
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeUnsignedLeb128(bytes.size)
        write(bytes)
    }

    fun writeTo(output: BufferWriter) {
        output.write(buffer.array(), 0, buffer.position())
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
