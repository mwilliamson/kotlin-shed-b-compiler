package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.*
import org.shedlang.compiler.frontend.types.StaticBindings
import org.shedlang.compiler.frontend.types.applyType
import org.shedlang.compiler.frontend.types.replaceTypes
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
            fields = mapOf()
        )
        assertThat(
            applyType(shape, listOf(BoolType, IntType)),
            isShapeType(
                name = equalTo("Pair"),
                typeArguments = isSequence(isBoolType, isIntType)
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
            applyType(shape, listOf(BoolType, IntType)),
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
            fields = mapOf("value" to applyType(innerShapeType, listOf(shapeTypeParameter)))
        )

        assertThat(
            applyType(shapeType, listOf(BoolType)),
            isShapeType(fields = listOf(
                "value" to isEquivalentType(applyType(innerShapeType, listOf(BoolType)))
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
            applyType(union, listOf(BoolType, IntType)),
            isUnionType(
                name = equalTo("Either"),
                typeArguments = isSequence(isBoolType, isIntType)
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
            members = listOf(applyType(shapeType, listOf(unionTypeParameter)) as ShapeType)
        )

        assertThat(
            applyType(union, listOf(BoolType)),
            isUnionType(members = isSequence(
                isEquivalentType(applyType(shapeType, listOf(BoolType)))
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
                replaceTypes(functionType, StaticBindings(types = mapOf(typeParameter to IntType))),
                isFunctionType(positionalParameters = isSequence(isIntType))
            )
        }

        @Test
        fun namedArgumentsAreReplaced() {
            val functionType = functionType(
                namedParameters = mapOf("x" to typeParameter)
            )

            assertThat(
                replaceTypes(functionType, StaticBindings(types = mapOf(typeParameter to IntType))),
                isFunctionType(namedParameters = isMap("x" to isIntType))
            )
        }

        @Test
        fun returnTypeIsReplaced() {
            val functionType = functionType(
                returns = typeParameter
            )

            assertThat(
                replaceTypes(functionType, StaticBindings(types = mapOf(typeParameter to IntType))),
                isFunctionType(returnType= isIntType)
            )
        }
    }
}
