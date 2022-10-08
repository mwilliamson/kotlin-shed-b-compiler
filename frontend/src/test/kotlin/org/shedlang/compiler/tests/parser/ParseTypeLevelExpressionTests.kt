package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.parser.PositionalParameterAfterNamedParameterError
import org.shedlang.compiler.frontend.parser.parseTypeLevelExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.throwsException

class ParseTypeLevelExpressionTests {
    @Test
    fun identifierIsParsedAsTypeReference() {
        val source = "T"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isTypeLevelReferenceNode(name = "T"))
    }

    @Test
    fun typeLevelFieldAccessIsParsed() {
        val source = "M.T"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isTypeLevelFieldAccessNode(
            receiver = isTypeLevelReferenceNode(name = "M"),
            fieldName = isIdentifier("T")
        ))
    }

    @Test
    fun typeApplicationIsRepresentedBySquareBrackets() {
        val source = "X[T, U]"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isTypeLevelApplicationNode(
            receiver = isTypeLevelReferenceNode(name = "X"),
            arguments = isSequence(
                isTypeLevelReferenceNode(name = "T"),
                isTypeLevelReferenceNode(name = "U")
            )
        ))
    }

    @Test
    fun functionTypeIsRepresentedByParenthesisedArgumentsThenArrowThenReturnType() {
        val source = "Fun (A, B) -> C"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionTypeNode(
            positionalParameters = isSequence(
                isTypeLevelReferenceNode(name = "A"),
                isTypeLevelReferenceNode(name = "B")
            ),
            returnType = isTypeLevelReferenceNode(name = "C")
        ))
    }

    @Test
    fun parametersCanHaveTrailingComma() {
        val source = "Fun (A,) -> C"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionTypeNode(
            positionalParameters = isSequence(
                isTypeLevelReferenceNode(name = "A")
            ),
            returnType = isTypeLevelReferenceNode(name = "C")
        ))
    }

    @Test
    fun functionTypeCanHaveNamedArguments() {
        val source = "Fun (A, B, .c: C) -> C"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionTypeNode(
            positionalParameters = isSequence(
                isTypeLevelReferenceNode(name = "A"),
                isTypeLevelReferenceNode(name = "B")
            ),
            namedParameters = isSequence(
                isFunctionTypeNamedParameterNode(name = "c", typeReference = "C")
            ),
            returnType = isTypeLevelReferenceNode(name = "C")
        ))
    }

    @Test
    fun functionTypeCannotHavePositionalParameterAfterNamedParameter() {
        val source = "Fun (.a: A, B) -> C"

        val node = { parseString(::parseTypeLevelExpression, source) }

        assertThat(node, throwsException<PositionalParameterAfterNamedParameterError>())
    }

    @Test
    fun functionTypeCanHaveEffects() {
        val source = "Fun () !E -> R"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionTypeNode(
            effect = present(isTypeLevelReferenceNode("E"))
        ))
    }

    @Test
    fun functionTypeTypeLevelParametersAreRepresentedBySquareBrackets() {
        val source = "Fun [T, U](T, U) -> R"
        val node = parseString(::parseTypeLevelExpression, source)
        assertThat(node, isFunctionTypeNode(

            positionalParameters = isSequence(
                isTypeLevelReferenceNode(name = "T"),
                isTypeLevelReferenceNode(name = "U")
            ),
            returnType = isTypeLevelReferenceNode(name = "R")
        ))
    }
}
