package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ArgumentNode
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

    @Test
    fun canReadOneArgumentFunctionSignature() {
        val source = "fun f(x: Int) : Unit { }"
        val function = parseString(::tryParseFunction, source)!!
        assertThat(function, has(FunctionNode::arguments, isSequence(
            isArgument("x", "Int")
        )))
    }

    @Test
    fun canReadManyArgumentFunctionSignature() {
        val source = "fun f(x: Int, y: String) : Unit { }"
        val function = parseString(::tryParseFunction, source)!!
        assertThat(function, has(FunctionNode::arguments, isSequence(
            isArgument("x", "Int"),
            isArgument("y", "String")
        )))
    }

    private fun isArgument(name: String, typeReference: String): Matcher<ArgumentNode> {
        return allOf(
            has(ArgumentNode::name, equalTo(name)),
            has(ArgumentNode::type, cast(
                has(TypeReferenceNode::name, equalTo(typeReference))
            ))
        )
    }
}