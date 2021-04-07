package org.shedlang.compiler.backends.wasm.wasm

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

internal object WasmBinaryFormat {
    internal fun write(module: WasmModule, output: OutputStream) {
        val writer = WasmBinaryFormatWriter(output)
        writer.write(module)
    }
}

private class WasmBinaryFormatWriter(private val outputStream: OutputStream) {
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
        output.writeUnsignedLeb128(typeIndex(params = descriptor.params, results = descriptor.results))
    }

    private fun typeIndex(params: List<WasmValueType>, results: List<WasmValueType>): Int {
        return typeIndices.getValue(Wasm.T.funcType(params = params, results = results))
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
        output.write8(type.binaryEncoding)
    }

    private fun writeSection(sectionType: SectionType, writeContents: (BufferWriter) -> Unit) {
        outputStream.write8(sectionType.id)
        val bufferWriter = BufferWriter()
        writeContents(bufferWriter)
        outputStream.writeUnsignedLeb128(bufferWriter.size)
        bufferWriter.writeTo(outputStream)
    }
}


private fun OutputStream.write8(byte: Byte) {
    write(byte.toInt())
}

private fun OutputStream.writeUnsignedLeb128(value: Int) {
    var currentValue = value.toUInt()
    while (true) {
        if ((currentValue shr 7) == 0u) {
            write8((currentValue and 0x7Fu).toByte())
            return
        } else {
            write8(((currentValue and 0x7Fu) or 0x80u).toByte())
            currentValue = currentValue shr 7
        }
    }
}

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

    fun write(bytes: ByteArray) {
        val minCapacity = buffer.position() + bytes.size
        if (minCapacity > buffer.capacity()) {
            var newCapacity = buffer.capacity() * 2
            while (minCapacity > newCapacity) {
                newCapacity *= 2
            }
            val newBuffer = ByteBuffer.allocate(newCapacity)
            newBuffer.put(buffer)
            buffer = newBuffer
        }
        buffer.put(bytes)
    }

    fun write8(byte: Byte) {
        write(byteArrayOf(byte))
    }

    fun writeVecSize(size: Int) {
        writeUnsignedLeb128(size)
    }

    fun writeUnsignedLeb128(value: Int) {
        var currentValue = value.toUInt()
        while (true) {
            if ((currentValue shr 7) == 0u) {
                write8((currentValue and 0x7Fu).toByte())
                return
            } else {
                write8(((currentValue and 0x7Fu) or 0x80u).toByte())
                currentValue = currentValue shr 7
            }
        }
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeUnsignedLeb128(bytes.size)
        write(bytes)
    }
}
