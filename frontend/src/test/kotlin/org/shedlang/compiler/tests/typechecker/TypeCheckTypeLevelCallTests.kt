package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.types.*

class TypeCheckTypeLevelCallTests {
    @Test
    fun typeLevelCallReplacesTypeParameters() {
        val boxReference = typeLevelReference("Box")
        val boolReference = typeLevelReference("Bool")

        val typeParameter = invariantTypeParameter("T")
        val boxType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val node = typeLevelCall(boxReference, listOf(boolReference))

        val type = inferType(
            node,
            typeContext(referenceTypes = mapOf(
                boxReference to TypeLevelValueType(boxType),
                boolReference to BoolMetaType
            ))
        )

        assertThat(type, isMetaType(
            isConstructedType(
                constructor = equalTo(boxType),
                args = isSequence(isBoolType),
            )
        ))
    }
}
