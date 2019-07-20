package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.parser.parseImport
import org.shedlang.compiler.tests.isIdentifier

class ParseImportTests {
    @Test
    fun moduleNameIsParsedFromImport() {
        val source = "import example.x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(
            name = isIdentifier("x"),
            path = equalTo(
                ImportPath.absolute(listOf("example", "x"))
            )
        ))
    }

    @Test
    fun moduleNameIsNormalised() {
        val source = "import example .  x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(
            name = isIdentifier("x"),
            path = equalTo(
                ImportPath.absolute(listOf("example", "x"))
            )
        ))
    }

    @Test
    fun relativeImportsStartWithDot() {
        val source = "import .example.x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(
            name = isIdentifier("x"),
            path = equalTo(
                ImportPath.relative(listOf("example", "x"))
            )
        ))
    }
}
