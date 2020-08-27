package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
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
            isShapeType(
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
            isShapeType(fields = isSequence(
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
            isShapeType(fields = isSequence(
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
    class FunctionTypeTests {
        val typeParameter = invariantTypeParameter("T")

        @Test
        fun positionalArgumentsAreReplaced() {
            val functionType = functionType(
                positionalParameters = listOf(typeParameter)
            )

            assertThat(
                replaceStaticValuesInType(functionType, StaticBindingsMap(mapOf(typeParameter to IntType))),
                isFunctionType(positionalParameters = isSequence(isIntType))
            )
        }

        @Test
        fun namedArgumentsAreReplaced() {
            val functionType = functionType(
                namedParameters = mapOf(Identifier("x") to typeParameter)
            )

            assertThat(
                replaceStaticValuesInType(functionType, StaticBindingsMap(mapOf(typeParameter to IntType))),
                isFunctionType(namedParameters = isMap(Identifier("x") to isIntType))
            )
        }

        @Test
        fun returnTypeIsReplaced() {
            val functionType = functionType(
                returns = typeParameter
            )

            assertThat(
                replaceStaticValuesInType(functionType, StaticBindingsMap(mapOf(typeParameter to IntType))),
                isFunctionType(returnType= isIntType)
            )
        }
    }

    @Nested
    class TupleTypeTests {
        val typeParameter = invariantTypeParameter("T")

        @Test
        fun elementsAreReplaced() {
            val tupleType = TupleType(
                elementTypes = listOf(typeParameter)
            )

            assertThat(
                replaceStaticValuesInType(tupleType, StaticBindingsMap(mapOf(typeParameter to IntType))),
                isTupleType(elementTypes = isSequence(isIntType))
            )
        }
    }
}
