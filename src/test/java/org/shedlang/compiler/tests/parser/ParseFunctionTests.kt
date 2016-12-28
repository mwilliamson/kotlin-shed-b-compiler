package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionNode
import org.shedlang.compiler.ast.TypeReferenceNode
import org.shedlang.compiler.parser.tryParseFunction
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.isSequence

class ParseFunctionTests {
    @Test
    fun canReadZeroArgumentFunctionSignature() {
        val source = "fun f() : Unit { }"
        val function = parseString(::tryParseFunction, source)!!
        assertThat(function, allOf(
            has(FunctionNode::name, equalTo("f")),
            has(FunctionNode::arguments, isSequence()),
            has(FunctionNode::returnType, cast(
                has(TypeReferenceNode::name, equalTo("Unit"))
            ))
        ))
    }
}