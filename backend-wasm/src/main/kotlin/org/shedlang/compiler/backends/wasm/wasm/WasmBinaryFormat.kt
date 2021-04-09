package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.backends.wasm.LateIndex
import java.io.OutputStream
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.nio.channels.Channels

internal object WasmBinaryFormat {
    internal fun write(module: WasmModule, output: OutputStream, lateIndices: Map<LateIndex, Int>) {
        val writer = WasmBinaryFormatWriter(outputStream = output, lateIndices = lateIndices)
        writer.write(module)
    }
}

@ExperimentalUnsignedTypes
private class WasmBinaryFormatWriter(
    private val lateIndices: Map<LateIndex, Int>,
    private val outputStream: OutputStream,
) {
    private val WASM_MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
    private val WASM_VERSION = byteArrayOf(0x01, 0x00, 0x00, 0x00)
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
        writeMemorySection(module)
        writeGlobalsSection(module)
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
        }
    }

    private fun writeGlobal(global: WasmGlobal, output: BufferWriter) {
        writeValueType(global.type, output)
        output.write8(if (global.mutable) 0x01 else 0x00)
        writeExpression(listOf(global.value), output)
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
        output.writeVecSize(function.locals.size)
        for (local in function.locals) {
            output.writeUnsignedLeb128(1)
            writeValueType(local.type, output)
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
        output.write8(0x41) // i32.const
        output.writeSignedLeb128(dataSegment.offset)
        output.write8(0x0B) // end
        output.writeVecSize(dataSegment.bytes.size)
        output.write(dataSegment.bytes)
    }

    private fun writeImport(import: WasmImport, output: BufferWriter) {
        output.writeString(import.moduleName)
        output.writeString(import.entityName)
        when (import.descriptor) {
            is WasmImportDescriptor.Function ->
                writeImportDescriptionFunction(import.descriptor, output)
        }
    }

    private fun writeImportDescriptionFunction(descriptor: WasmImportDescriptor.Function, output: BufferWriter) {
        output.write8(0x00)
        val funcType = Wasm.T.funcType(params = descriptor.params, results = descriptor.results)
        writeTypeIndex(funcType, output)
    }

    private fun writeLimits(min: Int, output: BufferWriter) {
        output.write8(0x00)
        output.writeUnsignedLeb128(min)
    }

    private fun writeTypeIndex(funcType: WasmFuncType, output: BufferWriter) {
        output.writeUnsignedLeb128(typeIndex(funcType))
    }

    private fun typeIndex(funcType: WasmFuncType): Int {
        return typeIndices.getValue(funcType)
    }

    private fun writeFuncType(type: WasmFuncType, output: BufferWriter) {
        typeIndices[type] = typeIndices.size
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
            is WasmInstruction.Folded.I32Const -> {
                output.write8(0x41)
                output.writeSignedLeb128(constValueToInt(instruction.value))
            }

            else ->
                throw UnsupportedOperationException(instruction.toString())
        }
    }

    private fun constValueToInt(value: WasmConstValue): Int {
        return when (value) {
            is WasmConstValue.I32 -> value.value
            is WasmConstValue.LateIndex -> lateIndices[value.ref]!!
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
        val minCapacity = buffer.position() + size - offset
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
        write(Leb128Encoding.encodeUnsignedInt32(value))
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
