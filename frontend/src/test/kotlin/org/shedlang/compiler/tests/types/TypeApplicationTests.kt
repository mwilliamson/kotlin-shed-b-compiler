package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.testing.isMap
import org.shedlang.compiler.testing.isSequence
import org.shedlang.compiler.testing.parametrizedShapeType
import org.shedlang.compiler.testing.parametrizedUnionType
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class TypeApplicationTests {
    @Test
    fun applyingTypeToShapeUpdatesTypeArguments() {
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
    fun applyingTypeToShapeReplacesTypeParameters() {
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
    fun applyingTypeToUnionReplacesTypeParameters() {
        val typeParameter1 = invariantTypeParameter("T")
        val typeParameter2 = invariantTypeParameter("U")
        val union = parametrizedUnionType(
            "Either",
            parameters = listOf(typeParameter1, typeParameter2),
            members = listOf(typeParameter1, typeParameter2)
        )
        assertThat(
            applyType(union, listOf(BoolType, IntType)),
            isUnionType(members = isSequence(isBoolType, isIntType))
        )
    }

    @Test
    fun applyingTypeToUnionReplacesTypeParametersInMembers() {
        val shapeTypeParameter = invariantTypeParameter("U")
        val shapeType = parametrizedShapeType(
            "Shape",
            listOf(shapeTypeParameter)
        )

        val unionTypeParameter = invariantTypeParameter("T")
        val union = parametrizedUnionType(
            "Union",
            parameters = listOf(unionTypeParameter),
            members = listOf(applyType(shapeType, listOf(unionTypeParameter)))
        )

        assertThat(
            applyType(union, listOf(BoolType)),
            isUnionType(members = isSequence(
                isEquivalentType(applyType(shapeType, listOf(BoolType)))
            ))
        )
    }

    @Test
    fun applyingTypeToShapeReplacesTypeParametersInFields() {
        val unionTypeParameter = invariantTypeParameter("T")
        val union = parametrizedUnionType(
            "Union",
            parameters = listOf(unionTypeParameter),
            members = listOf(unionTypeParameter)
        )

        val shapeTypeParameter = invariantTypeParameter("U")
        val shapeType = parametrizedShapeType(
            "Shape",
            parameters = listOf(shapeTypeParameter),
            fields = mapOf("value" to applyType(union, listOf(shapeTypeParameter)))
        )

        assertThat(
            applyType(shapeType, listOf(BoolType)),
            isShapeType(fields = listOf(
                "value" to isEquivalentType(applyType(union, listOf(BoolType)))
            ))
        )
    }

    @Nested
    class FunctionTypeTests {
        val typeParameter = invariantTypeParameter("T")

        @Test
        fun positionalArgumentsAreReplaced() {
            val functionType = functionType(
                positionalArguments = listOf(typeParameter)
            )

            assertThat(
                replaceTypes(functionType, StaticBindings(types = mapOf(typeParameter to IntType))),
                isFunctionType(arguments = isSequence(isIntType))
            )
        }

        @Test
        fun namedArgumentsAreReplaced() {
            val functionType = functionType(
                namedArguments = mapOf("x" to typeParameter)
            )

            assertThat(
                replaceTypes(functionType, StaticBindings(types = mapOf(typeParameter to IntType))),
                isFunctionType(namedArguments = isMap("x" to isIntType))
            )
        }

        @Test
        fun returnTypeIsReplaced() {
            val functionType = functionType(
                returns = typeParameter
            )

            assertThat(
                replaceTypes(functionType, StaticBindings(types = mapOf(typeParameter to IntType))),
                isFunctionType(returnType = isIntType)
            )
        }
    }
}
