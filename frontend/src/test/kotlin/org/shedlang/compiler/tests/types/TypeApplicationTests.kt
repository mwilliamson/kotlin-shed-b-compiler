package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class TypeApplicationTests {
    @Test
    fun applyingTypeToShapeCreatesConstructedType() {
        val typeParameter1 = invariantTypeParameter("T")
        val typeParameter2 = invariantTypeParameter("U")
        val shape = parametrizedShapeType(
            "Pair",
            parameters = listOf(typeParameter1, typeParameter2),
            fields = listOf()
        )
        assertThat(
            applyTypeLevel(shape, listOf(BoolType, IntType)),
            isConstructedType(
                constructor = equalTo(shape),
                args = isSequence(isBoolType, isIntType)
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
            applyTypeLevel(shape, listOf(BoolType, IntType)),
            isConstructedType(fields = present(isSequence(
                isField(name = isIdentifier("first"), type = isBoolType),
                isField(name = isIdentifier("second"), type = isIntType)
            )))
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
            fields = listOf(field("value", applyTypeLevel(innerShapeType, listOf(shapeTypeParameter)) as Type))
        )

        assertThat(
            applyTypeLevel(shapeType, listOf(BoolType)),
            isConstructedType(fields = present(isSequence(
                isField(name = isIdentifier("value"), type = isEquivalentType(applyTypeLevel(innerShapeType, listOf(BoolType)) as Type))
            )))
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
            applyTypeLevel(union, listOf(BoolType, IntType)),
            isConstructedType(
                constructor = equalTo(union),
                args = isSequence(isBoolType, isIntType)
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
            members = listOf(applyTypeLevel(shapeType, listOf(unionTypeParameter)) as ShapeType)
        )

        val result = applyTypeLevel(union, listOf(BoolType))

        assertThat(result, isUnionType(
            members = isSequence(
                isEquivalentType(applyTypeLevel(shapeType, listOf(BoolType)) as Type)
            )
        ))
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
                replaceTypeLevelValuesInType(functionType, mapOf(typeParameter to IntType)),
                isFunctionType(positionalParameters = isSequence(isIntType))
            )
        }

        @Test
        fun namedArgumentsAreReplaced() {
            val functionType = functionType(
                namedParameters = mapOf(Identifier("x") to typeParameter)
            )

            assertThat(
                replaceTypeLevelValuesInType(functionType, mapOf(typeParameter to IntType)),
                isFunctionType(namedParameters = isMap(Identifier("x") to isIntType))
            )
        }

        @Test
        fun returnTypeIsReplaced() {
            val functionType = functionType(
                returns = typeParameter
            )

            assertThat(
                replaceTypeLevelValuesInType(functionType, mapOf(typeParameter to IntType)),
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
                replaceTypeLevelValuesInType(tupleType, mapOf(typeParameter to IntType)),
                isTupleType(elementTypes = isSequence(isIntType))
            )
        }
    }
}
