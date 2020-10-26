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

    @Test
    fun whenStaticCallToEmptyDoesNotHaveOneArgumentThenErrorIsThrown() {
        val emptyReference = staticReference("Empty")
        val application = staticApplication(emptyReference, listOf())

        val type = {
            evalType(
                application,
                typeContext(referenceTypes = mapOf(
                    emptyReference to StaticValueType(EmptyTypeFunction)
                ))
            )
        }

        assertThat(type, throwsException(allOf(
            has(WrongNumberOfStaticArgumentsError::actual, equalTo(0)),
            has(WrongNumberOfStaticArgumentsError::expected, equalTo(1)),
        )))
    }

    @Test
    fun whenEmptyArgumentIsNotShapeTypeThenErrorIsThrown() {
        val emptyReference = staticReference("Empty")
        val intReference = staticReference("Int")
        val application = staticApplication(emptyReference, listOf(intReference))

        val type = {
            evalType(
                application,
                typeContext(referenceTypes = mapOf(
                    emptyReference to StaticValueType(EmptyTypeFunction),
                    intReference to metaType(IntType),
                ))
            )
        }

        assertThat(type, throwsException(allOf(
            has(UnexpectedTypeError::actual, isIntType),
            has(UnexpectedTypeError::expected, equalTo(ShapeTypeGroup)),
        )))
    }
}
