package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.types.*


class TypeCheckPartialCallTests {
    @Test
    fun partialCallWithPositionalArgumentReturnsFunction() {
        val functionReference = variableReference("f")
        val node = partialCall(
            receiver = functionReference,
            positionalArguments = listOf(literalInt())
        )

        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                positionalParameters = listOf(IntType, BoolType),
                namedParameters = mapOf(),
                effect = IoEffect,
                returns = UnitType
            )
        ))
        val type = inferType(node, typeContext)

        assertThat(type, isFunctionType(
            positionalParameters = isSequence(isBoolType),
            namedParameters = isMap(),
            effect = equalTo(IoEffect),
            returnType = isUnitType
        ))
    }

    @Test
    fun partialCallWithNamedArgumentReturnsFunction() {
        val functionReference = variableReference("f")
        val node = partialCall(
            receiver = functionReference,
            namedArguments = listOf(
                callNamedArgument("arg0", literalInt())
            )
        )

        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                positionalParameters = listOf(BoolType),
                namedParameters = mapOf(Identifier("arg0") to IntType, Identifier("arg1") to StringType),
                effect = IoEffect,
                returns = UnitType
            )
        ))
        val type = inferType(node, typeContext)

        assertThat(type, isFunctionType(
            positionalParameters = isSequence(isBoolType),
            namedParameters = isMap(Identifier("arg1") to isStringType),
            effect = equalTo(IoEffect),
            returnType = isUnitType
        ))
    }

    @Test
    fun canPartiallyCallConstructors() {
        val constructorReference = variableReference("f")
        val node = partialCall(
            receiver = constructorReference,
            namedArguments = listOf(
                callNamedArgument("x", literalInt())
            )
        )

        val shapeType = shapeType(
            fields = listOf(
                field(name = "x", type = IntType),
                field(name = "y", type = StringType),
            )
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            constructorReference to metaType(shapeType)
        ))

        val type = inferType(node, typeContext)

        assertThat(type, isFunctionType(
            positionalParameters = isSequence(),
            namedParameters = isMap(Identifier("y") to isStringType),
            effect = equalTo(EmptyEffect),
            returnType = isType(shapeType),
        ))
    }

    @Test
    fun partialCallBindsTypeParameters() {
        val functionReference = variableReference("f")
        val node = partialCall(
            receiver = functionReference,
            positionalArguments = listOf(literalInt())
        )

        val typeParameter = invariantTypeParameter("T")

        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                staticParameters = listOf(typeParameter),
                positionalParameters = listOf(typeParameter, typeParameter),
                namedParameters = mapOf(Identifier("x") to typeParameter),
                returns = typeParameter
            )
        ))
        val type = inferType(node, typeContext)

        assertThat(type, isFunctionType(
            staticParameters = isSequence(),
            positionalParameters = isSequence(isIntType),
            namedParameters = isMap(Identifier("x") to isIntType),
            returnType = isIntType
        ))
    }

    @Test
    fun partialCallBindsEffectParameters() {
        val functionReference = variableReference("f")
        val variableReference = variableReference("x")
        val node = partialCall(
            receiver = functionReference,
            positionalArguments = listOf(variableReference)
        )

        val effectParameter = effectParameter("E")

        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                staticParameters = listOf(effectParameter),
                positionalParameters = listOf(
                    functionType(effect = effectParameter),
                    functionType(effect = effectParameter)
                ),
                effect = effectParameter,
                returns = UnitType
            ),
            variableReference to functionType(effect = IoEffect)
        ))
        val type = inferType(node, typeContext)

        assertThat(type, isFunctionType(
            staticParameters = isSequence(),
            positionalParameters = isSequence(
                isFunctionType(effect = equalTo(IoEffect))
            ),
            effect = equalTo(IoEffect)
        ))
    }
}
