package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class TypeApplicationTests {
    @Test
    fun applyingTypeToShapeReplaceTypeParametersInTypeArguments() {
        val typeParameter1 = invariantTypeParameter("T")
        val typeParameter2 = invariantTypeParameter("U")
        val shape = parametrizedShapeType(
            "Pair",
            parameters = listOf(typeParameter1, typeParameter2),
            fields = listOf()
        )
        assertThat(
            applyStatic(shape, listOf(BoolType, IntType)),
            isCompleteShapeType(
                name = isIdentifier("Pair"),
                staticArguments = isSequence(isBoolType, isIntType)
            )
        )
    }

    @Test
    fun applyingTypeToShapeReplacesFieldTypes() {
        val typeParameter1 = invariantTypeParameter("T")
        val typeParameter2 = invariantTypeParameter("U")
        val shape = parametrizedShapeType(
            "Pair",
            parameters = listOf(typeParameter1, typeParameter2),
            fields = listOf(
                field("first", typeParameter1),
                field("second", typeParameter2)
            )
        )
        assertThat(
            applyStatic(shape, listOf(BoolType, IntType)),
            isCompleteShapeType(fields = isSequence(
                isField(name = isIdentifier("first"), type = isBoolType),
                isField(name = isIdentifier("second"), type = isIntType)
            ))
        )
    }

    @Test
    fun applyingTypeToShapeReplacesTypeParametersInFields() {
        val innerShapeTypeParameter = invariantTypeParameter("T")
        val innerShapeType = parametrizedShapeType(
            "InnerShapeType",
            parameters = listOf(innerShapeTypeParameter),
            fields = listOf(field("field", innerShapeTypeParameter))
        )

        val shapeTypeParameter = invariantTypeParameter("U")
        val shapeType = parametrizedShapeType(
            "Shape",
            parameters = listOf(shapeTypeParameter),
            fields = listOf(field("value", applyStatic(innerShapeType, listOf(shapeTypeParameter)) as Type))
        )

        assertThat(
            applyStatic(shapeType, listOf(BoolType)),
            isCompleteShapeType(fields = isSequence(
                isField(name = isIdentifier("value"), type = isEquivalentType(applyStatic(innerShapeType, listOf(BoolType)) as Type))
            ))
        )
    }

    @Test
    fun applyingTypeToUnionUpdatesTypeArguments() {
        val typeParameter1 = invariantTypeParameter("T")
        val typeParameter2 = invariantTypeParameter("U")
        val union = parametrizedUnionType(
            "Either",
            parameters = listOf(typeParameter1, typeParameter2),
            members = listOf()
        )
        assertThat(
            applyStatic(union, listOf(BoolType, IntType)),
            isUnionType(
                name = isIdentifier("Either"),
                staticArguments = isSequence(isBoolType, isIntType)
            )
        )
    }

    @Test
    fun applyingTypeToUnionReplacesTypeParametersInMembers() {
        val shapeTypeParameter = invariantTypeParameter("U")
        val shapeType = parametrizedShapeType(
            "Shape",
            listOf(shapeTypeParameter),
            fields = listOf(field("value", shapeTypeParameter))
        )

        val unionTypeParameter = invariantTypeParameter("T")
        val union = parametrizedUnionType(
            "Union",
            parameters = listOf(unionTypeParameter),
            members = listOf(applyStatic(shapeType, listOf(unionTypeParameter)) as ShapeType)
        )

        assertThat(
            applyStatic(union, listOf(BoolType)),
            isUnionType(members = isSequence(
                isEquivalentType(applyStatic(shapeType, listOf(BoolType)) as Type)
            ))
        )
    }

    @Nested
    inner class FunctionTypeTests {
        val typeParameter = invariantTypeParameter("T")

        @Test
        fun positionalArgumentsAreReplaced() {
            val functionType = functionType(
                positionalParameters = listOf(typeParameter)
            )

            assertThat(
                replaceStaticValuesInType(functionType, mapOf(typeParameter to IntType)),
                isFunctionType(positionalParameters = isSequence(isIntType))
            )
        }

        @Test
        fun namedArgumentsAreReplaced() {
            val functionType = functionType(
                namedParameters = mapOf(Identifier("x") to typeParameter)
            )

            assertThat(
                replaceStaticValuesInType(functionType, mapOf(typeParameter to IntType)),
                isFunctionType(namedParameters = isMap(Identifier("x") to isIntType))
            )
        }

        @Test
        fun returnTypeIsReplaced() {
            val functionType = functionType(
                returns = typeParameter
            )

            assertThat(
                replaceStaticValuesInType(functionType, mapOf(typeParameter to IntType)),
                isFunctionType(returnType= isIntType)
            )
        }
    }

    @Nested
    inner class TupleTypeTests {
        val typeParameter = invariantTypeParameter("T")

        @Test
        fun elementsAreReplaced() {
            val tupleType = TupleType(
                elementTypes = listOf(typeParameter)
            )

            assertThat(
                replaceStaticValuesInType(tupleType, mapOf(typeParameter to IntType)),
                isTupleType(elementTypes = isSequence(isIntType))
            )
        }
    }

    @Nested
    inner class UpdatedTypeTests {
        private val shapeId = freshNodeId()
        private val field = field(name = "x", shapeId = shapeId, type = IntType)
        private val shapeType = shapeType("Shape", shapeId = shapeId, fields = listOf(field))

        @Test
        fun baseTypeIsUpdated() {
            val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
            val newTypeParameter = invariantTypeParameter("T", shapeId = shapeId)
            val updatedType = updatedType(
                baseType = typeParameter,
                shapeType = shapeType,
                field = field
            )

            val result = replaceStaticValuesInType(updatedType, mapOf(typeParameter to newTypeParameter))

            assertThat(
                result,
                isUpdatedType(baseType = isType(newTypeParameter))
            )
        }

        @Test
        fun shapeTypeIsUpdated() {
            val typeParameter = invariantTypeParameter("T")
            val field = field(name = "x", shapeId = shapeId, type = typeParameter)
            val updatedType = updatedType(
                baseType = invariantTypeParameter("Base", shapeId = shapeId),
                shapeType = shapeType("Shape", shapeId = shapeId, fields = listOf(field)),
                field = field
            )

            val result = replaceStaticValuesInType(updatedType, mapOf(typeParameter to IntType))

            assertThat(
                result,
                isUpdatedType(shapeType = isCompleteShapeType(
                    fields = isSequence(
                        isField(type = isIntType))
                    )
                )
            )
        }

        @Test
        fun fieldIsUpdated() {
            val typeParameter = invariantTypeParameter("T")
            val field = field(name = "x", shapeId = shapeId, type = typeParameter)
            val updatedType = updatedType(
                baseType = invariantTypeParameter("Base", shapeId = shapeId),
                shapeType = shapeType("Shape", shapeId = shapeId, fields = listOf(field)),
                field = field
            )

            val result = replaceStaticValuesInType(updatedType, mapOf(typeParameter to IntType))
            
            assertThat(
                result,
                isUpdatedType(field = isField(type = isIntType))
            )
        }

        @Test
        fun whenBaseTypeIsUpdatedToShapeTypeThenResultIsShapeType() {
            val field1 = field(name = "field1", shapeId = shapeId, type = IntType)
            val field2 = field(name = "field2", shapeId = shapeId, type = IntType)
            val field3 = field(name = "field3", shapeId = shapeId, type = IntType)
            val shapeType = shapeType("Shape", shapeId = shapeId, fields = listOf(field1, field2, field3))

            val typeParameter = invariantTypeParameter("T", shapeId = shapeId)
            val updatedType = updatedType(
                baseType = typeParameter,
                shapeType = shapeType,
                field = field2
            )

            val newBaseType = createPartialShapeType(shapeType, populatedFieldNames = setOf(Identifier("field1")))
            val result = replaceStaticValuesInType(updatedType, mapOf(typeParameter to newBaseType))

            assertThat(
                result,
                isShapeType(
                    shapeId = equalTo(shapeId),
                    populatedFields = isSequence(
                        isField(name = isIdentifier("field1")),
                        isField(name = isIdentifier("field2")),
                    )
                )
            )
        }
    }
}
