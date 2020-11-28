package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.types.*

class TypeCheckStaticCallTests {
    @Test
    fun staticCallReplacesTypeParameters() {
        val boxReference = staticReference("Box")
        val boolReference = staticReference("Bool")

        val typeParameter = invariantTypeParameter("T")
        val boxType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val node = staticCall(boxReference, listOf(boolReference))

        val type = inferType(
            node,
            typeContext(referenceTypes = mapOf(
                boxReference to StaticValueType(boxType),
                boolReference to BoolMetaType
            ))
        )

        assertThat(type, isMetaType(isCompleteShapeType(
            name = isIdentifier("Box"),
            staticArguments = isSequence(isBoolType),
            fields = isSequence(
                isField(name = isIdentifier("value"), type = isBoolType)
            )
        )))
    }
}
