package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.fieldAccess
import org.shedlang.compiler.tests.isIntType
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.tests.variableReference
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.typechecker.NoSuchFieldError
import org.shedlang.compiler.types.UnitType
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

    @Test
    fun whenShapeHasNoSuchFieldThenErrorIsThrown() {
        val receiver = variableReference("x")
        val node = fieldAccess(receiver = receiver, fieldName = "y")
        val shapeType = shapeType(name = "X")

        val typeContext = typeContext(referenceTypes = mapOf(receiver to shapeType))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(NoSuchFieldError::fieldName, equalTo("y")))
        )
    }

    @Test
    fun whenReceiverIsNotShapeThenErrorIsThrown() {
        val receiver = variableReference("x")
        val node = fieldAccess(receiver = receiver, fieldName = "y")
        val shapeType = UnitType

        val typeContext = typeContext(referenceTypes = mapOf(receiver to shapeType))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(NoSuchFieldError::fieldName, equalTo("y")))
        )
    }
}
