package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.field
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.invariantTypeParameter
import org.shedlang.compiler.types.updatedType

class UpdatedTypeTests {
    @Test
    fun shortDescriptionIncludesBaseTypeAndField() {
        val shapeId = freshNodeId()
        val field = field(name = "x", shapeId = shapeId, type = IntType)
        val shapeType = shapeType("Shape", shapeId = shapeId, fields = listOf(field))

        val updatedType = updatedType(
            baseType = invariantTypeParameter("T", shapeId = shapeId),
            shapeType = shapeType,
            field = field
        )

        assertThat(updatedType.shortDescription, equalTo("T + @(Shape.fields.x: Int)"))
    }
}
