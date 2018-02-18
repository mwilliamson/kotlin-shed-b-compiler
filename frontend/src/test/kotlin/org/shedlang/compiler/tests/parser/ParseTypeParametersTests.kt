package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.TokenIterator
import org.shedlang.compiler.parser.TokenType
import org.shedlang.compiler.parser.parseTypeParameters
import org.shedlang.compiler.frontend.tests.isContravariant
import org.shedlang.compiler.frontend.tests.isCovariant
import org.shedlang.compiler.frontend.tests.isInvariant
import org.shedlang.compiler.tests.isSequence

class ParseTypeParametersTests {
    @Test
    fun canParseSingleInvariantTypeParameter() {
        val typeParameters = parseString(Companion::parseTypeParametersWithVariance, "[T]")
        assertThat(typeParameters, isSequence(
            isTypeParameter(name = equalTo("T"), variance = isInvariant)
        ))
    }

    @Test
    fun canParseSingleCovariantTypeParameter() {
        val typeParameters = parseString(Companion::parseTypeParametersWithVariance, "[+T]")
        assertThat(typeParameters, isSequence(
            isTypeParameter(name = equalTo("T"), variance = isCovariant)
        ))
    }

    @Test
    fun canParseSingleContravariantTypeParameter() {
        val typeParameters = parseString(Companion::parseTypeParametersWithVariance, "[-T]")
        assertThat(typeParameters, isSequence(
            isTypeParameter(name = equalTo("T"), variance = isContravariant)
        ))
    }

    companion object {
        private fun parseTypeParametersWithVariance(tokens: TokenIterator<TokenType>)
            = parseTypeParameters(allowVariance = true, tokens = tokens)
    }
}
