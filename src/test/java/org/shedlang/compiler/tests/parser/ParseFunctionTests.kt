package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionNode
import org.shedlang.compiler.ast.TypeReferenceNode
import org.shedlang.compiler.parser.tryParseFunction
import org.shedlang.compiler.tests.allOf

class ParseFunctionTests {
    @Test
    fun canReadZeroArgumentFunctionSignature() {
        val source = "fun f() : Unit { }"
        val function = parseString(::tryParseFunction, source)!!
        assertThat(function, allOf(
            has(FunctionNode::name, equalTo("f")),
            has(FunctionNode::returnType, isA<TypeReferenceNode>(
                has(TypeReferenceNode::name, equalTo("Unit"))
            ))
        ))
    }
}