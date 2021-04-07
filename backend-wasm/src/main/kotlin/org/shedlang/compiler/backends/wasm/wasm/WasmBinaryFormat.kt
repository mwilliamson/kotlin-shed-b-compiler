package org.shedlang.compiler.backends.wasm.wasm

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

internal object WasmBinaryFormat {
    private val WASM_MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
    private val WASM_VERSION = byteArrayOf(0x01, 0x00, 0x00, 0x00)

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

    internal fun write(module: WasmModule, output: OutputStream) {
        output.write(WASM_MAGIC)
        output.write(WASM_VERSION)
        writeTypesSection(module, output)
    }

    private fun writeTypesSection(module: WasmModule, output: OutputStream) {
        if (module.types.size > 0) {
            writeSection(SectionType.TYPE, output) { output ->
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

    private fun writeFuncType(type: WasmFuncType, output: BufferWriter) {
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

    private fun writeSection(sectionType: SectionType, output: OutputStream, writeContents: (BufferWriter) -> Unit) {
        output.write8(sectionType.id)
        val bufferWriter = BufferWriter()
        writeContents(bufferWriter)
        output.writeUnsignedLeb128(bufferWriter.size)
        bufferWriter.writeTo(output)
    }
}

private fun OutputStream.write8(byte: Byte) {
    write(byte.toInt())
}

private fun OutputStream.write32(int: Int) {
    write(ByteBuffer.allocate(4).putInt(int).array())
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
}
