package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.parser.ParseError
import org.shedlang.compiler.parser.PositionalArgumentAfterNamedArgumentError
import org.shedlang.compiler.parser.UnexpectedTokenError
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence

class ParseCallTests {
    @Test
    fun canParseFunctionCallWithNoArguments() {
        val source = "x()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            isVariableReferenceNode("x"),
            isSequence()
        ))
    }

    @Test
    fun canParseFunctionCallWithOneArgument() {
        val source = "x(y)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            isVariableReferenceNode("x"),
            isSequence(isVariableReferenceNode("y"))
        ))
    }

    @Test
    fun canParseFunctionCallWithManyArguments() {
        val source = "x(y, z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            isVariableReferenceNode("x"),
            isSequence(isVariableReferenceNode("y"), isVariableReferenceNode("z"))
        ))
    }

    @Test
    fun canParseFunctionCallWithNamedArgument() {
        val source = "x(.y = z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            receiver = isVariableReferenceNode("x"),
            positionalArguments = isSequence(),
            fieldArguments = isSequence(
                isNamedArgumentNode(
                    name = isIdentifier("y"),
                    expression = isVariableReferenceNode("z")
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
        assertThat(node, isCallNode(
            fieldArguments = isSequence(
                isSplatArgumentNode(expression = isVariableReferenceNode("y")),
            ),
        ))
    }

    @Test
    fun canParseFunctionCallWithExplicitTypeArgument() {
        val source = "f[T]()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            typeArguments = isSequence(isTypeLevelReferenceNode("T"))
        ))
    }

    @Test
    fun canParseFunctionCallWithExplicitTypeArguments() {
        val source = "f[T, U]()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            typeArguments = isSequence(isTypeLevelReferenceNode("T"), isTypeLevelReferenceNode("U"))
        ))
    }

    @Test
    fun explicitTypeArgumentsMustHaveAtLeastOneArgument() {
        val source = "f[]()"
        assertThat(
            { parseString(::parseExpression, source) },
            throws<UnexpectedTokenError>()
        )
    }

    @Test
    fun callWithoutBangHasNoEffects() {
        val source = "x()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            hasEffect = equalTo(false)
        ))
    }

    @Test
    fun callWithBangHasEffects() {
        val source = "x!()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCallNode(
            hasEffect = equalTo(true)
        ))
    }

    @Test
    fun canParsePartialCall() {
        val source = "f ~ (x, .y = z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isPartialCallNode(
            receiver = isVariableReferenceNode("f"),
            positionalArguments = isSequence(
                isVariableReferenceNode("x")
            ),
            fieldArguments = isSequence(
                isNamedArgumentNode(
                    name = isIdentifier("y"),
                    expression = isVariableReferenceNode("z")
                )
            )
        ))
    }

    @Test
    fun canParseTypeLevelCall() {
        val source = "x[A, B]"

        val node = parseString(::parseExpression, source)

        assertThat(node, isTypeLevelCallNode(
            receiver = isVariableReferenceNode("x"),
            arguments = isSequence(
                isTypeLevelReferenceNode("A"),
                isTypeLevelReferenceNode("B"),
            )
        ))
    }

    @Test
    fun whenCallHasBangThenCallIsNotTreatedAsTypeLevelCall() {
        val source = "x![A, B]"

        assertThat({
            parseString(::parseExpression, source)
        }, throwsException<ParseError>())
    }
}
