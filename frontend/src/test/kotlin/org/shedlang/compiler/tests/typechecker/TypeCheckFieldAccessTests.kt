package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.fieldAccess
import org.shedlang.compiler.tests.isIntType
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.tests.variableReference
import org.shedlang.compiler.typechecker.IntType
import org.shedlang.compiler.typechecker.inferType

class TypeCheckFieldAccessTests {
    @Test
    fun typeOfFieldAccessIsTypeOfField() {
        val receiver = variableReference("x")
        val node = fieldAccess(receiver = receiver, fieldName = "y")
        val shapeType = shapeType(name = "X", fields = mapOf("y" to IntType))

        val typeContext = typeContext(referenceTypes = mapOf(receiver to shapeType))
        val type = inferType(node, typeContext)

        assertThat(type, isIntType)
    }
}
