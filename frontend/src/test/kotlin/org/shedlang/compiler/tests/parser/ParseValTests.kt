package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.parser.parseFunctionStatement
import org.shedlang.compiler.frontend.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier

class ParseValTests {
    @Test
    fun valIsValidFunctionStatement() {
        val source = "val x = 4;"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isValNode(
            target = isTargetVariableNode(name = isIdentifier("x")),
            expression = isIntLiteralNode(4)
        ))
    }

    @Test
    fun valIsValidModuleStatement() {
        val source = "val x = 4;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isValNode(
            target = isTargetVariableNode(name = isIdentifier("x")),
            expression = isIntLiteralNode(4)
        ))
    }
}
