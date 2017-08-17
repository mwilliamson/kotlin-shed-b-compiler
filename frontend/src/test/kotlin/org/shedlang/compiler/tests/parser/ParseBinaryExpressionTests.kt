package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.parser.ParseError
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isSequence

class ParseBinaryExpressionTests {
    @Test
    fun canParseEquality() {
        val source = "x == y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.EQUALS,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseAddition() {
        val source = "x + y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.ADD,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseSubtraction() {
        val source = "x - y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.SUBTRACT,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

    @Test
    fun canParseMultiplication() {
        val source = "x * y"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.MULTIPLY,
            isVariableReference("x"),
            isVariableReference("y")
        ))
    }

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
        val source = "x(y=z)"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            receiver = isVariableReference("x"),
            positionalArguments = isSequence(),
            namedArguments = isSequence(
                isCallNamedArgument(
                    name = equalTo("y"),
                    expression = isVariableReference("z")
                )
            )
        ))
    }

    @Test
    fun positionalArgumentCannotAppearAfterNamedArgument() {
        val source = "f(x=y, z)"
        assertThat(
            { parseString(::parseExpression, source) },
            throws(has(ParseError::message, equalTo("Positional argument cannot appear after named argument")))
        )
    }

    @Test
    fun canParseFunctionCallWithExplicitTypeArgument() {
        val source = "f[T]()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            typeArguments = isSequence(isTypeReference("T"))
        ))
    }

    @Test
    fun canParseFunctionCallWithExplicitTypeArguments() {
        val source = "f[T, U]()"
        val node = parseString(::parseExpression, source)
        assertThat(node, isCall(
            typeArguments = isSequence(isTypeReference("T"), isTypeReference("U"))
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
    fun canParseLeftAssociativeOperationsWithThreeOperands() {
        val source = "x + y + z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.ADD,
            isBinaryOperation(
                Operator.ADD,
                isVariableReference("x"),
                isVariableReference("y")
            ),
            isVariableReference("z")
        ))
    }

    @Test
    fun canParseLeftAssociativeOperationsWithFourOperands() {
        val source = "a + b + c + d"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.ADD,
            isBinaryOperation(
                Operator.ADD,
                isBinaryOperation(
                    Operator.ADD,
                    isVariableReference("a"),
                    isVariableReference("b")
                ),
                isVariableReference("c")
            ),
            isVariableReference("d")
        ))
    }

    @Test
    fun higherPrecedenceOperatorsBindMoreTightlyThanLowerPrecedenceOperators() {
        val source = "x + y * z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.ADD,
            isVariableReference("x"),
            isBinaryOperation(
                Operator.MULTIPLY,
                isVariableReference("y"),
                isVariableReference("z")
            )

        ))
    }

    @Test
    fun canUseParensToGroupExpressions() {
        val source = "(x + y) * z"
        val node = parseString(::parseExpression, source)
        assertThat(node, isBinaryOperation(
            Operator.MULTIPLY,
            isBinaryOperation(
                Operator.ADD,
                isVariableReference("x"),
                isVariableReference("y")
            ),
            isVariableReference("z")
        ))
    }
}
