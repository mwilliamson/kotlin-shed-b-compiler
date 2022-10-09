package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.shapeType

class SimpleShapeTypeTests {
    @Test
    fun shortDescriptionOfSimpleShapeIsNameOfShape() {
        val type = shapeType(name = "Shape")

        val result = type.shortDescription

        assertThat(result, equalTo("Shape"))
    }
}
