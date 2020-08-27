package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.field
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.invariantTypeParameter
import org.shedlang.compiler.types.updatedType

class UpdatedTypeTests {
    private val shapeId = freshNodeId()
    private val field = field(name = "x", shapeId = shapeId, type = IntType)
    private val shapeType = shapeType("Shape", shapeId = shapeId, fields = listOf(field))

    @Test
    fun whenBaseTypeIsNotShapeThenErrorIsThrown() {
        assertThat({
            updatedType(baseType = invariantTypeParameter("T"), shapeType = shapeType(), field = field)
        }, throws<CompilerError>(has(CompilerError::message, equalTo("cannot update non-shape type"))))
    }

    @Test
    fun shortDescriptionIncludesBaseTypeAndField() {

        val updatedType = updatedType(
            baseType = invariantTypeParameter("T", shapeId = shapeId),
            shapeType = shapeType,
            field = field
        )

        assertThat(updatedType.shortDescription, equalTo("T + @(Shape.fields.x: Int)"))
    }
}
