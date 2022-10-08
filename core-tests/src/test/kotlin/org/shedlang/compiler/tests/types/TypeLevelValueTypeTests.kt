package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class TypeLevelValueTypeTests {
    private val typeRegistry = TypeRegistryImpl()

    @Test
    fun shortDescriptionContainsType() {
        val type = metaType(IntType)

        assertThat(type.shortDescription, equalTo("TypeLevelValue[Int]"))
    }

    @Test
    fun nonShapeTypesHaveFieldsOfUnitType() {
        val type = metaType(IntType)
        val typeRegistry = TypeRegistryImpl()

        val result = typeRegistry.fieldType(type, Identifier("fields"))

        assertThat(result, present(isUnitType))
    }

    @Test
    fun canReadNameOfFieldsFromShapeTypeInfo() {
        val type = metaType(shapeType(fields = listOf(
            field("first", type = IntType),
            field("second", type = StringType)
        )))

        val fieldsField = typeRegistry.fieldType(type, Identifier("fields"))!!
        val firstField = typeRegistry.fieldType(fieldsField, Identifier("first"))!!
        val secondField = typeRegistry.fieldType(fieldsField, Identifier("second"))!!
        assertThat(typeRegistry.fieldType(fieldsField, Identifier("third")), absent())
        assertThat(typeRegistry.fieldType(firstField, Identifier("name")), present(isStringType))
        assertThat(typeRegistry.fieldType(secondField, Identifier("name")), present(isStringType))
    }

    @Test
    fun canReadGetterOfFieldFromShapeTypeInfo() {
        val shapeType = shapeType(fields = listOf(
            field("first", type = IntType),
            field("second", type = StringType)
        ))
        val metaType = metaType(shapeType)

        val fieldsField = typeRegistry.fieldType(metaType, Identifier("fields"))!!
        val firstField = typeRegistry.fieldType(fieldsField, Identifier("first"))!!
        val secondField = typeRegistry.fieldType(fieldsField, Identifier("second"))!!

        assertThat(
            typeRegistry.fieldType(firstField, Identifier("get")),
            present(isEquivalentType(functionType(
                positionalParameters = listOf(shapeType),
                returns = IntType
            )))
        )
        assertThat(
            typeRegistry.fieldType(secondField, Identifier("get")),
            present(isEquivalentType(functionType(
                positionalParameters = listOf(shapeType),
                returns = StringType
            )))
        )
    }
}
