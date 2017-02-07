package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseShape
import org.shedlang.compiler.tests.isSequence

class ParseShapeTests {
    @Test
    fun emptyShape() {
        val source = "shape X { }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            name = equalTo("X"),
            fields = isSequence()
        ))
    }

    @Test
    fun shapeHasCommaSeparatedFields() {
        val source = "shape X { a: Int, b: String }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            fields = isSequence(
                isShapeField(
                    name = equalTo("a"),
                    type = isTypeReference("Int")
                ),
                isShapeField(
                    name = equalTo("b"),
                    type = isTypeReference("String")
                )
            )
        ))
    }
}
