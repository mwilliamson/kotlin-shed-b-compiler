package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType

class MetaTypeTests {
    @Test
    fun shortDescriptionContainsType() {
        val type = MetaType(IntType)

        assertThat(type.shortDescription, equalTo("Type[Int]"))
    }
}
