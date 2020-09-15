package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.types.createPartialShapeType

class ShapeTypeTests {
    @Test
    fun shortDescriptionOfSimpleShapeIsNameOfShape() {
        val type = shapeType(name = "Shape")

        val result = type.shortDescription

        assertThat(result, equalTo("Shape"))
    }

    @Test
    fun shortDescriptionOfShapeWithSubsetOfFieldListsPopulatedFields() {
        val completeType = shapeType(name = "Shape")
        val partialType = createPartialShapeType(completeType, setOf(Identifier("x"), Identifier("y")))

        val result = partialType.shortDescription

        assertThat(result, equalTo("Partial[Shape, .x, .y]"))
    }
}
