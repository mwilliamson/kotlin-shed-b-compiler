package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.parser.parseTypesModuleStatement
import org.shedlang.compiler.tests.isIdentifier

class ParseEffectDeclarationTests {
    @Test
    fun canParseEffect() {
        val source = "effect Write;"

        val node = parseString(::parseTypesModuleStatement, source)

        assertThat(node, isEffectDeclarationNode(
            name = isIdentifier("Write")
        ))
    }
}
