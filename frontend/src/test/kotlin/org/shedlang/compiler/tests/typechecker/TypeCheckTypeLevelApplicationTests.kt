package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.types.*

class TypeCheckTypeLevelApplicationTests {
    @Test
    fun parametrizedShapeCallHasTypeOfShapeWithTypeParametersReplaced() {
        val listReference = typeLevelReference("Box")
        val boolReference = typeLevelReference("Bool")

        val typeParameter = invariantTypeParameter("T")
        val listType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val application = typeLevelApplication(listReference, listOf(boolReference))

        val type = evalType(
            application,
            typeContext(referenceTypes = mapOf(
                listReference to TypeLevelValueType(listType),
                boolReference to BoolMetaType
            ))
        )

        assertThat(type, isConstructedType(
            constructor = equalTo(listType),
            args = isSequence(isBoolType),
        ))
    }
}
