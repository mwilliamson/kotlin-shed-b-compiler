package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.parser.ParseError
import org.shedlang.compiler.parser.PositionalArgumentAfterNamedArgumentError
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence

class ParseCallTests {
    @Test
    fun canParseFunctionCallWithNoArguments() {
        val source = "x()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            isVariableReference("x"),
            isSequence()
        ))
    }

    @Test
    fun canParseFunctionCallWithOneArgument() {
        val source = "x(y)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            isVariableReference("x"),
            isSequence(isVariableReference("y"))
        ))
    }

    @Test
    fun canParseFunctionCallWithManyArguments() {
        val source = "x(y, z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            isVariableReference("x"),
            isSequence(isVariableReference("y"), isVariableReference("z"))
        ))
    }

    @Test
    fun canParseFunctionCallWithNamedArgument() {
        val source = "x(.y = z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            receiver = isVariableReference("x"),
            positionalArguments = isSequence(),
            fieldArguments = isSequence(
                isNamedArgument(
                    name = isIdentifier("y"),
                    expression = isVariableReference("z")
                )
            )
        ))
    }

    @Test
    fun positionalArgumentCannotAppearAfterNamedArgument() {
        val source = "f(.x = y, z)"
        assertThat(
            { parseString(::parseExpression, source) },
            throwsException<PositionalArgumentAfterNamedArgumentError>()
        )
    }

    @Test
    fun canParseFunctionCallWithSplatArguments() {
        val source = "x(...y)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            fieldArguments = isSequence(
                isSplatArgument(expression = isVariableReference("y")),
            ),
        ))
    }

    @Test
    fun canParseFunctionCallWithExplicitTypeArgument() {
        val source = "f[T]()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            typeArguments = isSequence(isStaticReference("T"))
        ))
    }

    @Test
    fun canParseFunctionCallWithExplicitTypeArguments() {
        val source = "f[T, U]()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            typeArguments = isSequence(isStaticReference("T"), isStaticReference("U"))
        ))
    }

    @Test
    fun explicitTypeArgumentsMustHaveAtLeastOneArgument() {
        val source = "f[]()"
        assertThat(
            { parseString(::parseExpression, source) },
            throws<UnexpectedTokenException>()
        )
    }

    @Test
    fun callWithoutBangHasNoEffects() {
        val source = "x()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            hasEffect = equalTo(false)
        ))
    }

    @Test
    fun callWithBangHasEffects() {
        val source = "x!()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            hasEffect = equalTo(true)
        ))
    }

    @Test
    fun canParsePartialCall() {
        val source = "f ~ (x, .y = z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isPartialCall(
            receiver = isVariableReference("f"),
            positionalArguments = isSequence(
                isVariableReference("x")
            ),
            fieldArguments = isSequence(
                isNamedArgument(
                    name = isIdentifier("y"),
                    expression = isVariableReference("z")
                )
            )
        ))
    }

    @Test
    fun canParseStaticCall() {
        val source = "x[A, B]"

        val node = parseString(::parseExpression, source)

        assertThat(node, isStaticCall(
            receiver = isVariableReference("x"),
            arguments = isSequence(
                isStaticReference("A"),
                isStaticReference("B"),
            )
        ))
    }

    @Test
    fun whenCallHasBangThenCallIsNotTreatedAsStaticCall() {
        val source = "x![A, B]"

        assertThat({
            parseString(::parseExpression, source)
        }, throwsException<ParseError>())
    }
}
