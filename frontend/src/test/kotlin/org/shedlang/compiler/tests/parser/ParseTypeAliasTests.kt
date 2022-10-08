package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier

class ParseTypeAliasTests {
    @Test
    fun canParseTypeAlias() {
        val source = "type Size = Int;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isTypeAliasNode(
            name = isIdentifier("Size"),
            expression = isTypeLevelReferenceNode("Int")
        ))
    }
}
