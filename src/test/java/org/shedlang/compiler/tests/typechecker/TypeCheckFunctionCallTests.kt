package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*

class TypeCheckFunctionCallTests {
    @Test
    fun functionCallTypeIsReturnTypeOfFunction() {
        val node = functionCall(function = variableReference("f"))
        val type = inferType(node, typeContext(variables = mapOf(Pair("f", FunctionType(listOf(), IntType)))))
        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun whenFunctionExpressionIsNotFunctionTypeThenCallDoesNotTypeCheck() {
        val node = functionCall(
            function = variableReference("f"),
            arguments = listOf(literalInt(1), literalBool(true))
        )
        assertThat(
            { inferType(node, typeContext(variables = mapOf(Pair("f", IntType)))) },
            throwsUnexpectedType(expected = FunctionType(listOf(IntType, BoolType), AnyType), actual = IntType)
        )
    }

    @Test
    fun errorWhenArgumentTypesDoNotMatch() {
        val node = functionCall(
            function = variableReference("f"),
            arguments = listOf(literalInt(1))
        )
        val variables = mapOf(Pair("f", FunctionType(listOf(BoolType), IntType)))
        assertThat(
            { inferType(node, typeContext(variables = variables)) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun errorWhenExtraArgumentIsPassed() {
        val node = functionCall(
            function = variableReference("f"),
            arguments = listOf(literalInt(1))
        )
        val variables = mapOf(Pair("f", FunctionType(listOf(), IntType)))
        assertThat(
            { inferType(node, typeContext(variables = variables)) },
            throws(allOf(
                has(WrongNumberOfArgumentsError::expected, equalTo(0)),
                has(WrongNumberOfArgumentsError::actual, equalTo(1))
            ))
        )
    }

    @Test
    fun errorWhenArgumentIsMissing() {
        val node = functionCall(
            function = variableReference("f"),
            arguments = listOf()
        )
        val variables = mapOf(Pair("f", FunctionType(listOf(IntType), IntType)))
        assertThat(
            { inferType(node, typeContext(variables = variables)) },
            throws(allOf(
                has(WrongNumberOfArgumentsError::expected, equalTo(1)),
                has(WrongNumberOfArgumentsError::actual, equalTo(0))
            ))
        )
    }
}
