package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.field
import org.shedlang.compiler.tests.isEquivalentType
import org.shedlang.compiler.tests.isStringType
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.StaticValueType
import org.shedlang.compiler.types.StringType
import org.shedlang.compiler.types.functionType

class StaticValueTypeTests {
    @Test
    fun shortDescriptionContainsType() {
        val type = StaticValueType(IntType)

        assertThat(type.shortDescription, equalTo("StaticValue[Int]"))
    }

    @Test
    fun nonShapeTypesHaveNoFieldsField() {
        val type = StaticValueType(IntType)

        assertThat(type.fieldType(Identifier("fields")), absent())
    }

    @Test
    fun canReadNameOfFieldsFromShapeTypeInfo() {
        val type = StaticValueType(shapeType(fields = listOf(
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
        val metaType = StaticValueType(shapeType)

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
