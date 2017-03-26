package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseImport

class ParseImportTests {
    @Test
    fun moduleNameIsParsedFromImport() {
        val source = "import example.x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(module = equalTo("example.x")))
    }

    @Test
    fun moduleNameIsNormalised() {
        val source = "import example .  x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(module = equalTo("example.x")))
    }

    @Test
    fun relativeImportsStartWithDot() {
        val source = "import .example.x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(module = equalTo(".example.x")))
    }
}
