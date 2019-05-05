package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.TupleType

class TupleTypeTests {
    @Test
    fun shortDescriptionOfEmptyTupleHasNoElements() {
        val type = TupleType(listOf())

        assertThat(type.shortDescription, equalTo("#()"))
    }

    @Test
    fun shortDescriptionOfTupleWithElements() {
        val type = TupleType(listOf(IntType, BoolType))

        assertThat(type.shortDescription, equalTo("#(Int, Bool)"))
    }
}