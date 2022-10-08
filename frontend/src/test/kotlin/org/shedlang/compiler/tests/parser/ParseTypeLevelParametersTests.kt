package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.parser.TokenIterator
import org.shedlang.compiler.frontend.parser.TokenType
import org.shedlang.compiler.frontend.parser.parseTypeLevelParameters
import org.shedlang.compiler.tests.*

class ParseTypeLevelParametersTests {
    @Test
    fun canParseSingleInvariantTypeParameter() {
        val parameters = parseString(Companion::parseTypeLevelParametersWithVariance, "[T]")
        assertThat(parameters, isSequence(
            isTypeParameterNode(name = isIdentifier("T"), variance = isInvariant)
        ))
    }

    @Test
    fun canParseSingleCovariantTypeParameter() {
        val parameters = parseString(Companion::parseTypeLevelParametersWithVariance, "[+T]")
        assertThat(parameters, isSequence(
            isTypeParameterNode(name = isIdentifier("T"), variance = isCovariant)
        ))
    }

    @Test
    fun canParseSingleContravariantTypeParameter() {
        val parameters = parseString(Companion::parseTypeLevelParametersWithVariance, "[-T]")
        assertThat(parameters, isSequence(
            isTypeParameterNode(name = isIdentifier("T"), variance = isContravariant)
        ))
    }

    @Test
    fun canParseEffectParameter() {
        val parameters = parseString(Companion::parseTypeLevelParametersWithVariance, "[!E]")
        assertThat(parameters, isSequence(
            isEffectParameterNode(name = isIdentifier("E"))
        ))
    }

    companion object {
        private fun parseTypeLevelParametersWithVariance(tokens: TokenIterator<TokenType>)
            = parseTypeLevelParameters(allowVariance = true, tokens = tokens)
    }
}
