package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseTypeParameters
import org.shedlang.compiler.tests.isSequence

class ParseTypeParametersTests {
    @Test
    fun canParseSingleInvariantTypeParameter() {
        val typeParameters = parseString(::parseTypeParameters, "[T]")
        assertThat(typeParameters, isSequence(isTypeParameter(name = equalTo("T"))))
    }
}