package org.shedlang.compiler.backends.wasm.wasm

@ExperimentalUnsignedTypes
internal object Leb128Encoding {
    internal fun encodeUnsignedInt32(value: Int): UByteArray {
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

    internal fun encodeSignedInt32(initialValue: Int): UByteArray {
        val bytes = mutableListOf<UByte>()
        var value = initialValue
        var remaining = value shr 7
        var hasMore = true
        val end = if (value and Int.MIN_VALUE == 0) 0 else -1
        while (hasMore) {
            hasMore = (remaining != end
                || remaining and 1 != value shr 6 and 1)
            bytes.add((value and 0x7f or if (hasMore) 0x80 else 0).toByte().toUByte())
            value = remaining
            remaining = remaining shr 7
        }
        return bytes.toUByteArray()
    }
}
