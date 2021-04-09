package org.shedlang.compiler.backends.wasm.wasm

internal object Leb128Encoding {
    @ExperimentalUnsignedTypes
    internal fun encodeUnsignedInt32(value: Int, minLength: Int = 0): UByteArray {
        val bytes = mutableListOf<UByte>()
        var currentValue = value.toUInt()
        while (true) {
            if ((currentValue shr 7) == 0u) {
                bytes.add((currentValue and 0x7Fu).toUByte())
                return bytes.toUByteArray()
            } else {
                bytes.add(((currentValue and 0x7Fu) or 0x80u).toUByte())
                currentValue = currentValue shr 7
            }
        }
    }
}
