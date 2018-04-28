package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.parser.parseStaticExpression
import org.shedlang.compiler.tests.isSequence

class ParseStaticExpressionTests {
    @Test
    fun identifierIsParsedAsTypeReference() {
        val source = "T"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isStaticReference(name = "T"))
    }

    @Test
    fun staticFieldAccessIsParsed() {
        val source = "M.T"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isStaticFieldAccess(
            receiver = isStaticReference(name = "M"),
            fieldName = isIdentifier("T")
        ))
    }

    @Test
    fun typeApplicationIsRepresentedBySquareBrackets() {
        val source = "X[T, U]"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isStaticApplication(
            receiver = isStaticReference(name = "X"),
            arguments = isSequence(
                isStaticReference(name = "T"),
                isStaticReference(name = "U")
            )
        ))
    }

    @Test
    fun functionTypeIsRepresentedByParenthesisedArgumentsThenArrowThenReturnType() {
        val source = "(A, B) -> C"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isFunctionType(
            positionalParameters = isSequence(
                isStaticReference(name = "A"),
                isStaticReference(name = "B")
            ),
            returnType = isStaticReference(name = "C")
        ))
    }

    @Test
    fun parametersCanHaveTrailingComma() {
        val source = "(A,) -> C"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isFunctionType(
            positionalParameters = isSequence(
                isStaticReference(name = "A")
            ),
            returnType = isStaticReference(name = "C")
        ))
    }

    @Test
    fun functionTypeCanHaveNamedArguments() {
        val source = "(A, B, *, c: C) -> C"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isFunctionType(
            positionalParameters = isSequence(
                isStaticReference(name = "A"),
                isStaticReference(name = "B")
            ),
            namedParameters = isSequence(
                isParameter(name = "c", typeReference = "C")
            ),
            returnType = isStaticReference(name = "C")
        ))
    }

    @Test
    fun functionTypeCanHaveEffects() {
        val source = "() !E -> R"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isFunctionType(
            effects = isSequence(isStaticReference("E"))
        ))
    }

    @Test
    fun functionTypeStaticParametersAreRepresentedBySquareBrackets() {
        val source = "[T, U](T, U) -> R"
        val node = parseString(::parseStaticExpression, source)
        assertThat(node, isFunctionType(

            positionalParameters = isSequence(
                isStaticReference(name = "T"),
                isStaticReference(name = "U")
            ),
            returnType = isStaticReference(name = "R")
        ))
    }
}
