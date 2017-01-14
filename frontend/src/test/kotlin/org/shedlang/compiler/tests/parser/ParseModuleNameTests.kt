package org.shedlang.compiler.tests.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseModuleName

class ParseModuleNameTests {
    @Test
    fun moduleNameCanBeOneIdentifier() {
        val source = "abc"
        val name = parseString(::parseModuleName, source)
        assertEquals("abc", name)
    }

    @Test
    fun moduleNameCanBeIdentifiersSeparatedByDots() {
        val source = "abc.de"
        val name = parseString(::parseModuleName, source)
        assertEquals("abc.de", name)
    }
}
