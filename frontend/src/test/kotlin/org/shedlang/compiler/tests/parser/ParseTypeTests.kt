package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseType
import org.shedlang.compiler.tests.isSequence

class ParseTypeTests {
    @Test
    fun identifierIsParsedAsTypeReference() {
        val source = "T"
        val node = parseString(::parseType, source)
        assertThat(node, isTypeReference(name = "T"))
    }

    @Test
    fun staticFieldAccessIsParsed() {
        val source = "M.T"
        val node = parseString(::parseType, source)
        assertThat(node, isStaticFieldAccess(
            receiver = isTypeReference(name = "M"),
            fieldName = equalTo("T")
        ))
    }

    @Test
    fun typeApplicationIsRepresentedBySquareBrackets() {
        val source = "X[T, U]"
        val node = parseString(::parseType, source)
        assertThat(node, isTypeApplication(
            receiver = isTypeReference(name = "X"),
            arguments = isSequence(
                isTypeReference(name = "T"),
                isTypeReference(name = "U")
            )
        ))
    }

    @Test
    fun functionTypeIsRepresentedByParenthesisedArgumentsThenArrowThenReturnType() {
        val source = "(A, B) -> C"
        val node = parseString(::parseType, source)
        assertThat(node, isFunctionType(
            arguments = isSequence(
                isTypeReference(name = "A"),
                isTypeReference(name = "B")
            ),
            returnType = isTypeReference(name = "C")
        ))
    }

    @Test
    fun functionTypeCanHaveEffects() {
        val source = "() !E -> R"
        val node = parseString(::parseType, source)
        assertThat(node, isFunctionType(
            effects = isSequence(isVariableReference("!E"))
        ))
    }
}
