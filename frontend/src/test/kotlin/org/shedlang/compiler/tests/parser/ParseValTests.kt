package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.IntegerLiteralNode
import org.shedlang.compiler.parser.parseFunctionStatement
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier

class ParseValTests {
    @Test
    fun valIsValidFunctionStatement() {
        val source = "val x = 4;"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isVal(
            target = isValTarget(name = isIdentifier("x")),
            expression = has(IntegerLiteralNode::value, equalTo(4.toBigInteger()))
        ))
    }

    @Test
    fun valIsValidModuleStatement() {
        val source = "val x = 4;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isVal(
            target = isValTarget(name = isIdentifier("x")),
            expression = has(IntegerLiteralNode::value, equalTo(4.toBigInteger()))
        ))
    }
}
