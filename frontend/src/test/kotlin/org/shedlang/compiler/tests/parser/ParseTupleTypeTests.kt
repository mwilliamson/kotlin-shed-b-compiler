package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseTypeLevelExpression
import org.shedlang.compiler.tests.isSequence

class ParseTupleTypeTests {
    @Test
    fun emptyTupleType() {
        val source = "#()"
        val node = parseString(::parseTypeLevelExpression, source)

        assertThat(node, isTupleTypeNode(isSequence()))
    }

    @Test
    fun singletonTupleType() {
        val source = "#(X)"
        val node = parseString(::parseTypeLevelExpression, source)

        assertThat(node, isTupleTypeNode(isSequence(
            isTypeLevelReferenceNode("X")
        )))
    }

    @Test
    fun tupleTypeWithManyElements() {
        val source = "#(X, Y, Z)"
        val node = parseString(::parseTypeLevelExpression, source)

        assertThat(node, isTupleTypeNode(isSequence(
            isTypeLevelReferenceNode("X"),
            isTypeLevelReferenceNode("Y"),
            isTypeLevelReferenceNode("Z")
        )))
    }

    @Test
    fun elementsCanHaveTrailingComma() {
        val source = "#(X,)"
        val node = parseString(::parseTypeLevelExpression, source)

        assertThat(node, isTupleTypeNode(isSequence(
            isTypeLevelReferenceNode("X")
        )))
    }
}
