package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.WrongNumberOfStaticArgumentsError
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.types.*

class TypeCheckStaticStaticCallTests {
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
                boolReference to BoolMetaType
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
}
