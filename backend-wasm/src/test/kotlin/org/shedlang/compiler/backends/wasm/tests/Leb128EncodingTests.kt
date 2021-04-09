package org.shedlang.compiler.backends.wasm.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.wasm.wasm.Leb128Encoding
import org.shedlang.compiler.tests.isSequence

@ExperimentalUnsignedTypes
class Leb128EncodingTests {
    @Test
    fun unsignedEncoding() {
        assertThat(Leb128Encoding.encodeUnsignedInt32(0x0), isUByteArrayOf(0x00u))
        assertThat(Leb128Encoding.encodeUnsignedInt32(0x1), isUByteArrayOf(0x01u))
        assertThat(Leb128Encoding.encodeUnsignedInt32(0x7F), isUByteArrayOf(0x7Fu))
        assertThat(Leb128Encoding.encodeUnsignedInt32(0x80), isUByteArrayOf(0x80u, 0x01u))
    }

    fun isUByteArrayOf(vararg bytes: UByte): Matcher<UByteArray> {
        return isSequence(*bytes.map { byte -> equalTo(byte) }.toTypedArray())
    }
}
