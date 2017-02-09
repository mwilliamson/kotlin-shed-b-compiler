package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*

class TypeCheckFunctionCallTests {
    @Test
    fun functionCallTypeIsReturnTypeOfFunction() {
        val functionReference = variableReference("f")
        val node = functionCall(function = functionReference)

        val typeContext = typeContext(referenceTypes = mapOf(functionReference to positionalFunctionType(listOf(), IntType)))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun whenFunctionExpressionIsNotFunctionTypeThenCallDoesNotTypeCheck() {
        val functionReference = variableReference("f")
        val node = functionCall(
            function = functionReference,
            positionalArguments = listOf(literalInt(1), literalBool(true))
        )
        assertThat(
            { inferType(node, typeContext(referenceTypes = mapOf(functionReference to IntType))) },
            throwsUnexpectedType(expected = positionalFunctionType(listOf(IntType, BoolType), AnyType), actual = IntType)
        )
    }

    @Test
    fun errorWhenArgumentTypesDoNotMatch() {
        val functionReference = variableReference("f")
        val node = functionCall(
            function = functionReference,
            positionalArguments = listOf(literalInt(1))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to positionalFunctionType(listOf(BoolType), IntType)
        ))
        assertThat(
            { inferType(node, typeContext) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun errorWhenExtraArgumentIsPassed() {
        val functionReference = variableReference("f")
        val node = functionCall(
            function = functionReference,
            positionalArguments = listOf(literalInt(1))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to positionalFunctionType(listOf(), IntType)
        ))
        assertThat(
            { inferType(node, typeContext) },
            throws(allOf(
                has(WrongNumberOfArgumentsError::expected, equalTo(0)),
                has(WrongNumberOfArgumentsError::actual, equalTo(1))
            ))
        )
    }

    @Test
    fun errorWhenArgumentIsMissing() {
        val functionReference = variableReference("f")
        val node = functionCall(
            function = functionReference,
            positionalArguments = listOf()
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to positionalFunctionType(listOf(IntType), IntType)
        ))
        assertThat(
            { inferType(node, typeContext) },
            throws(allOf(
                has(WrongNumberOfArgumentsError::expected, equalTo(1)),
                has(WrongNumberOfArgumentsError::actual, equalTo(0))
            ))
        )
    }

    @Test
    fun shapeCallTypeIsShapeType() {
        val shapeReference = variableReference("X")
        val node = functionCall(function = shapeReference)

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(shapeType)))
    }

    @Test
    fun errorWhenShapeCallIsPassedPositionalArgument() {
        val shapeReference = variableReference("X")
        val node = functionCall(
            function = shapeReference,
            positionalArguments = listOf(literalBool())
        )

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws<PositionalArgumentPassedToShapeConstructorError>()
        )
    }

    @Test
    fun errorWhenShapeCallIsMissingField() {
        val shapeReference = variableReference("X")
        val node = functionCall(function = shapeReference)

        val shapeType = shapeType(name = "X", fields = mapOf("a" to BoolType))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(MissingArgumentError::argumentName, equalTo("a")))
        )
    }

    @Test
    fun errorWhenShapeCallIsPassedWrongTypeForField() {
        val shapeReference = variableReference("X")
        val node = functionCall(
            function = shapeReference,
            namedArguments = mapOf("a" to literalInt())
        )

        val shapeType = shapeType(name = "X", fields = mapOf("a" to BoolType))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun errorWhenShapeCallHasExtraField() {
        val shapeReference = variableReference("X")
        val node = functionCall(
            function = shapeReference,
            namedArguments = mapOf("a" to literalInt())
        )

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(ExtraArgumentError::argumentName, equalTo("a")))
        )
    }
}
