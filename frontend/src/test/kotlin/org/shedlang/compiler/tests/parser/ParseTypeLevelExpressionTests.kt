package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.PositionalParameterAfterNamedParameterError
import org.shedlang.compiler.parser.parseTypeLevelExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.throwsException

class ParseTypeLevelExpressionTests {
    @Test
    fun identifierIsParsedAsTypeReference() {
        val source = "T"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isTypeLevelReference(name = "T"))
    }

    @Test
    fun typeLevelFieldAccessIsParsed() {
        val source = "M.T"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isTypeLevelFieldAccess(
            receiver = isTypeLevelReference(name = "M"),
            fieldName = isIdentifier("T")
        ))
    }

    @Test
    fun typeApplicationIsRepresentedBySquareBrackets() {
        val source = "X[T, U]"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isTypeLevelApplication(
            receiver = isTypeLevelReference(name = "X"),
            arguments = isSequence(
                isTypeLevelReference(name = "T"),
                isTypeLevelReference(name = "U")
            )
        ))
    }

    @Test
    fun functionTypeIsRepresentedByParenthesisedArgumentsThenArrowThenReturnType() {
        val source = "Fun (A, B) -> C"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionType(
            positionalParameters = isSequence(
                isTypeLevelReference(name = "A"),
                isTypeLevelReference(name = "B")
            ),
            returnType = isTypeLevelReference(name = "C")
        ))
    }

    @Test
    fun parametersCanHaveTrailingComma() {
        val source = "Fun (A,) -> C"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionType(
            positionalParameters = isSequence(
                isTypeLevelReference(name = "A")
            ),
            returnType = isTypeLevelReference(name = "C")
        ))
    }

    @Test
    fun functionTypeCanHaveNamedArguments() {
        val source = "Fun (A, B, .c: C) -> C"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionType(
            positionalParameters = isSequence(
                isTypeLevelReference(name = "A"),
                isTypeLevelReference(name = "B")
            ),
            namedParameters = isSequence(
                isFunctionTypeNamedParameter(name = "c", typeReference = "C")
            ),
            returnType = isTypeLevelReference(name = "C")
        ))
    }

    @Test
    fun functionTypeCannotHavePositionalParameterAfterNamedParameter() {
        val source = "Fun (.a: A, B) -> C"

        val node = { parseString(::parseTypeLevelExpression, source) }

        assertThat(node, throwsException< PositionalParameterAfterNamedParameterError>())
    }

    @Test
    fun functionTypeCanHaveEffects() {
        val source = "Fun () !E -> R"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionType(
            effect = present(isTypeLevelReference("E"))
        ))
    }

    @Test
    fun functionTypeTypeLevelParametersAreRepresentedBySquareBrackets() {
        val source = "Fun [T, U](T, U) -> R"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionType(

            positionalParameters = isSequence(
                isTypeLevelReference(name = "T"),
                isTypeLevelReference(name = "U")
            ),
            returnType = isTypeLevelReference(name = "R")
        ))
    }
}
