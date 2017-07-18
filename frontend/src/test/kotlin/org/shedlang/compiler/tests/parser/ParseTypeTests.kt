package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
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
}
