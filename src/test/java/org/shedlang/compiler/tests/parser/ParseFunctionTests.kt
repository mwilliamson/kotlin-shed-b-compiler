package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionNode
import org.shedlang.compiler.parser.tryParseFunction
import org.shedlang.compiler.tests.allOf

class ParseFunctionTests {
    @Test
    fun canReadZeroArgumentFunctionSignature() {
        val source = "fun f() { }"
        val function = parseString(::tryParseFunction, source)!!
        assertThat(function, allOf(
            has(FunctionNode::name, equalTo("f"))
        ))
    }
}