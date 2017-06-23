package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckFunctionCallTests {
    @Test
    fun functionCallTypeIsReturnTypeOfFunction() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)

        val typeContext = typeContext(referenceTypes = mapOf(functionReference to positionalFunctionType(listOf(), IntType)))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun functionCallWithImplicitTypeArguments() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt())
        )

        val typeParameter = TypeParameter(name = "T")
        val functionType = functionType(
            typeParameters = listOf(typeParameter),
            positionalArguments = listOf(typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun functionCallWithImplicitTypeArgumentsWithTypeParameterInstantiatedToSameTypeTwice() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(), literalInt())
        )

        val typeParameter = TypeParameter(name = "T")
        val functionType = functionType(
            typeParameters = listOf(typeParameter),
            positionalArguments = listOf(typeParameter, typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun typeParameterTakesUnionTypeWhenUsedWithMultipleTypes() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(), literalString())
        )

        val typeParameter = TypeParameter(name = "T")
        val functionType = functionType(
            typeParameters = listOf(typeParameter),
            positionalArguments = listOf(typeParameter, typeParameter),
            returns = typeParameter
        )
        val typeContext = typeContext(referenceTypes = mapOf(functionReference to functionType))
        val type = inferType(node, typeContext)

        assertThat(type, isUnionType(members = isSequence(isIntType, isStringType)))
    }

    @Test
    fun whenFunctionExpressionIsNotFunctionTypeThenCallDoesNotTypeCheck() {
        val functionReference = variableReference("f")
        val node = call(
            receiver = functionReference,
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
        val node = call(
            receiver = functionReference,
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
        val node = call(
            receiver = functionReference,
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
        val node = call(
            receiver = functionReference,
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
        val node = call(receiver = shapeReference)

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))
        val type = inferType(node, typeContext)

        assertThat(type, cast(equalTo(shapeType)))
    }

    @Test
    fun errorWhenShapeCallIsPassedPositionalArgument() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
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
        val node = call(receiver = shapeReference)

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
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(callNamedArgument("a", literalInt()))
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
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(callNamedArgument("a", literalInt()))
        )

        val shapeType = shapeType(name = "X")
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(ExtraArgumentError::argumentName, equalTo("a")))
        )
    }

    @Test
    fun errorWhenSameNamedArgumentIsPassedMultipleTimes() {
        val shapeReference = variableReference("X")
        val node = call(
            receiver = shapeReference,
            namedArguments = listOf(
                callNamedArgument("a", literalInt()),
                callNamedArgument("a", literalInt())
            )
        )

        val shapeType = shapeType(name = "X", fields = mapOf("a" to IntType))
        val typeContext = typeContext(referenceTypes = mapOf(shapeReference to MetaType(shapeType)))

        assertThat(
            { inferType(node, typeContext) },
            throws(has(ArgumentAlreadyPassedError::argumentName, equalTo("a")))
        )
    }

    @Test
    fun whenEffectIsInScopeThenCanCallFunctionWithEffect() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)
        val functionType = functionType(
            effects = listOf(IoEffect),
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effects = listOf(IoEffect)
        )
        inferType(node, typeContext)
    }

    @Test
    fun errorWhenCallingFunctionWithEffectNotInScope() {
        val functionReference = variableReference("f")
        val node = call(receiver = functionReference)
        val functionType = functionType(
            effects = listOf(IoEffect),
            returns = UnitType
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to functionType),
            effects = listOf(object : Effect {})
        )
        assertThat(
            { inferType(node, typeContext) },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }
}
