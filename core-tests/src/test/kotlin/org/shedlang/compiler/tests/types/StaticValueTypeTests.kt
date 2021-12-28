package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class StaticValueTypeTests {
    @Test
    fun shortDescriptionContainsType() {
        val type = metaType(IntType)

        assertThat(type.shortDescription, equalTo("StaticValue[Int]"))
    }

    @Test
    fun nonShapeTypesHaveFieldsOfUnitType() {
        val type = metaType(IntType)

        assertThat(type.fieldType(Identifier("fields")), present(isUnitType))
    }

    @Test
    fun canReadNameOfFieldsFromShapeTypeInfo() {
        val type = metaType(shapeType(fields = listOf(
            field("first", type = IntType),
            field("second", type = StringType)
        )))

        val fieldsField = type.fieldType(Identifier("fields"))
        val firstField = fieldsField!!.fieldType(Identifier("first"))
        val secondField = fieldsField!!.fieldType(Identifier("second"))
        assertThat(fieldsField.fieldType(Identifier("third")), absent())

        assertThat(firstField?.fieldType(Identifier("name")), present(isStringType))
        assertThat(secondField?.fieldType(Identifier("name")), present(isStringType))
    }

    @Test
    fun canReadGetterOfFieldFromShapeTypeInfo() {
        val shapeType = shapeType(fields = listOf(
            field("first", type = IntType),
            field("second", type = StringType)
        ))
        val metaType = metaType(shapeType)

        val fieldsField = metaType.fieldType(Identifier("fields"))
        val firstField = fieldsField!!.fieldType(Identifier("first"))
        val secondField = fieldsField!!.fieldType(Identifier("second"))

        assertThat(
            firstField?.fieldType(Identifier("get")),
            present(isEquivalentType(functionType(
                positionalParameters = listOf(shapeType),
                returns = IntType
            )))
        )
        assertThat(
            secondField?.fieldType(Identifier("get")),
            present(isEquivalentType(functionType(
                positionalParameters = listOf(shapeType),
                returns = StringType
            )))
        )
    }
}
