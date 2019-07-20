package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.parser.parseImport

class ParseImportTests {
    @Test
    fun moduleNameIsParsedFromImport() {
        val source = "import a from example.x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(
            target = isTargetVariable("a"),
            path = equalTo(
                ImportPath.absolute(listOf("example", "x"))
            )
        ))
    }

    @Test
    fun moduleNameIsNormalised() {
        val source = "import a from example .  x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(
            target = isTargetVariable("a"),
            path = equalTo(
                ImportPath.absolute(listOf("example", "x"))
            )
        ))
    }

    @Test
    fun relativeImportsStartWithDot() {
        val source = "import a from .example.x;"
        val node = parseString(::parseImport, source)
        assertThat(node, isImport(
            target = isTargetVariable("a"),
            path = equalTo(
                ImportPath.relative(listOf("example", "x"))
            )
        ))
    }
}
