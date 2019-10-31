package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier

class ParseVarargsDeclarationTests {
    @Test
    fun canParseVarargsDeclaration() {
        val source = "varargs list(cons, nil)"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isVarargsDeclaration(
            name = isIdentifier("list"),
            cons = isVariableReference("cons"),
            nil = isVariableReference("nil")
        ))
    }
}
