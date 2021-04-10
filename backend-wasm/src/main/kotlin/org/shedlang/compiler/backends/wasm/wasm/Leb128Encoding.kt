package org.shedlang.compiler.backends.wasm.wasm

@ExperimentalUnsignedTypes
internal object Leb128Encoding {
    internal fun encodeUnsignedInt32(value: Int): UByteArray {
        val bytes = ArrayList<UByte>(5)
        var currentValue = value.toUInt()
        while (true) {
            val byte = currentValue and 0x7Fu
            if ((currentValue shr 7) == 0u) {
                bytes.add(byte.toUByte())
                return bytes.toUByteArray()
            } else {
                bytes.add((byte or 0x80u).toUByte())
                currentValue = currentValue shr 7
            }
        }
    }

    internal fun encodePaddedUnsignedInt32(value: Int): UByteArray {
        val bytes = UByteArray(5)
        var currentValue = value.toUInt()
        for (i in bytes.indices) {
            var byte = currentValue and 0x7Fu
            if (i < bytes.size - 1) {
                byte = byte or 0x80u
            }
            bytes[i] = byte.toUByte()
            currentValue = currentValue shr 7
        }
        return bytes
    }

    internal fun encodeSignedInt32(initialValue: Int): UByteArray {
        val bytes = ArrayList<UByte>(5)
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

    internal fun encodePaddedSignedInt32(initialValue: Int): UByteArray {
        val bytes = UByteArray(5)
        var value = initialValue
        for (i in bytes.indices) {
            var byte = value and 0x7f
            if (i < bytes.size - 1) {
                byte = byte or 0x80
            }
            bytes[i] = byte.toByte().toUByte()
            value = value shr 7
        }
        return bytes.toUByteArray()
    }
}
