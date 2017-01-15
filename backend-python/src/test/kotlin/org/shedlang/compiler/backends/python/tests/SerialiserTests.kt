package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.python.serialise

class SerialiserTests {
    @Test
    fun integerSerialisation() {
        val node = pythonLiteralInt(42)
        val output = serialise(node)
        assertThat(output, equalTo("42"))
    }
}
