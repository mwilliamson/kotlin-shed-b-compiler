package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseTypesModuleStatement
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence

class ParseEffectDeclarationTests {
    @Test
    fun canParseEffect() {
        val source = "effect Write;"

        val node = parseString(::parseTypesModuleStatement, source)

        assertThat(node, isEffectDeclaration(
            name = isIdentifier("Write"),
            staticParameters = isSequence()
        ))
    }

    @Test
    fun canParseEffectWithSingleTypeParameter() {
        val source = "effect Write[T];"

        val node = parseString(::parseTypesModuleStatement, source)

        assertThat(node, isEffectDeclaration(
            name = isIdentifier("Write"),
            staticParameters = isSequence(isTypeParameter(name = isIdentifier("T")))
        ))
    }
}
