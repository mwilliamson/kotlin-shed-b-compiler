package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.frontend.tests.*
import org.shedlang.compiler.tests.isMap
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.parametrizedShapeType
import org.shedlang.compiler.tests.parametrizedUnionType
import org.shedlang.compiler.types.*

class TypeApplicationTests {
    @Test
    fun applyingTypeToShapeReplaceTypeParametersInTypeArguments() {
        val typeParameter1 = invariantTypeParameter("T")
        val typeParameter2 = invariantTypeParameter("U")
        val shape = parametrizedShapeType(
            "Pair",
            parameters = listOf(typeParameter1, typeParameter2),
            fields = mapOf()
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
            fields = mapOf(
                "first" to typeParameter1,
                "second" to typeParameter2
            )
        )
        assertThat(
            applyStatic(shape, listOf(BoolType, IntType)),
            isShapeType(fields = listOf(
                "first" to isBoolType,
                "second" to isIntType
            ))
        )
    }

    @Test
    fun applyingTypeToShapeReplacesTypeParametersInFields() {
        val innerShapeTypeParameter = invariantTypeParameter("T")
        val innerShapeType = parametrizedShapeType(
            "InnerShapeType",
            parameters = listOf(innerShapeTypeParameter),
            fields = mapOf("field" to innerShapeTypeParameter)
        )

        val shapeTypeParameter = invariantTypeParameter("U")
        val shapeType = parametrizedShapeType(
            "Shape",
            parameters = listOf(shapeTypeParameter),
            fields = mapOf("value" to applyStatic(innerShapeType, listOf(shapeTypeParameter)))
        )

        assertThat(
            applyStatic(shapeType, listOf(BoolType)),
            isShapeType(fields = listOf(
                "value" to isEquivalentType(applyStatic(innerShapeType, listOf(BoolType)))
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
            fields = mapOf("value" to shapeTypeParameter)
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
                isEquivalentType(applyStatic(shapeType, listOf(BoolType)))
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
}
