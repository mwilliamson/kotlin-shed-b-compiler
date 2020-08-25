package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseTarget
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isPair
import org.shedlang.compiler.tests.isSequence

class ParseTargetTests {
    @Test
    fun underscoreIsParsedAsIgnore() {
        val source = "_"
        val node = parseString(::parseTarget, source)
        assertThat(node, isTargetIgnore())
    }

    @Test
    fun valTargetCanBeVariableReference() {
        val source = "x"
        val node = parseString(::parseTarget, source)
        assertThat(node, isTargetVariable(name = isIdentifier("x")))
    }

    @Test
    fun valTargetCanBeTuple() {
        val source = "#(x, y)"
        val node = parseString(::parseTarget, source)
        assertThat(node, isTargetTuple(
            elements = isSequence(
                isTargetVariable(name = isIdentifier("x")),
                isTargetVariable(name = isIdentifier("y"))
            )
        ))
    }

    @Test
    fun targetTupleCanHaveTrailingComma() {
        val source = "#(x,)"
        val node = parseString(::parseTarget, source)
        assertThat(node, isTargetTuple(
            elements = isSequence(
                isTargetVariable(name = isIdentifier("x"))
            )
        ))
    }

    @Test
    fun valTargetCanBeFields() {
        val source = "@(.x as targetX, .y as targetY)"
        val node = parseString(::parseTarget, source)
        assertThat(node, isTargetFields(
            fields = isSequence(
                isPair(isFieldName("x"), isTargetVariable(name = isIdentifier("targetX"))),
                isPair(isFieldName("y"), isTargetVariable(name = isIdentifier("targetY")))
            )
        ))
    }

    @Test
    fun valTargetFieldsCanHaveTrailingComma() {
        val source = "@(.x as targetX,)"
        val node = parseString(::parseTarget, source)
        assertThat(node, isTargetFields(
            fields = isSequence(
                isPair(isFieldName("x"), isTargetVariable(name = isIdentifier("targetX")))
            )
        ))
    }
}
