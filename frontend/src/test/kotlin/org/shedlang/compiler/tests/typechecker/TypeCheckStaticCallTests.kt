package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.types.*

class TypeCheckStaticCallTests {
    @Test
    fun parametrizedShapeCallHasTypeOfShapeWithTypeParametersReplaced() {
        val listReference = staticReference("Box")
        val boolReference = staticReference("Bool")

        val typeParameter = invariantTypeParameter("T")
        val listType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val application = staticApplication(listReference, listOf(boolReference))

        val type = evalType(
            application,
            typeContext(referenceTypes = mapOf(
                listReference to StaticValueType(listType),
                boolReference to StaticValueType(BoolType)
            ))
        )

        assertThat(type, isCompleteShapeType(
            name = isIdentifier("Box"),
            staticArguments = isSequence(isBoolType),
            fields = isSequence(
                isField(name = isIdentifier("value"), type = isBoolType)
            )
        ))
    }

    @Test
    fun emptyCallHasEmptyShapeType() {
        val boxReference = staticReference("Box")
        val emptyReference = staticReference("Empty")

        val boxType = shapeType(
            "Box",
            fields = listOf(
                field("value", IntType)
            )
        )
        val application = staticApplication(emptyReference, listOf(boxReference))

        val type = evalType(
            application,
            typeContext(referenceTypes = mapOf(
                boxReference to StaticValueType(boxType),
                emptyReference to StaticValueType(EmptyTypeFunction)
            ))
        )

        assertThat(type, isShapeType(
            name = isIdentifier("Box"),
            allFields = isSequence(isField(name = isIdentifier("value"))),
            populatedFields = isSequence()
        ))
    }
}
