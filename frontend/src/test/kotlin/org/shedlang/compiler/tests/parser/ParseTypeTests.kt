package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseType
import org.shedlang.compiler.testing.isSequence

class ParseTypeTests {
    @Test
    fun identifierIsParsedAsTypeReference() {
        val source = "T"
        val node = parseString(::parseType, source)
        assertThat(node, isStaticReference(name = "T"))
    }

    @Test
    fun staticFieldAccessIsParsed() {
        val source = "M.T"
        val node = parseString(::parseType, source)
        assertThat(node, isStaticFieldAccess(
            receiver = isStaticReference(name = "M"),
            fieldName = equalTo("T")
        ))
    }

    @Test
    fun typeApplicationIsRepresentedBySquareBrackets() {
        val source = "X[T, U]"
        val node = parseString(::parseType, source)
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
        val node = parseString(::parseType, source)
        assertThat(node, isFunctionType(
            arguments = isSequence(
                isStaticReference(name = "A"),
                isStaticReference(name = "B")
            ),
            returnType = isStaticReference(name = "C")
        ))
    }

    @Test
    fun functionTypeCanHaveEffects() {
        val source = "() !E -> R"
        val node = parseString(::parseType, source)
        assertThat(node, isFunctionType(
            effects = isSequence(isStaticReference("!E"))
        ))
    }
}
