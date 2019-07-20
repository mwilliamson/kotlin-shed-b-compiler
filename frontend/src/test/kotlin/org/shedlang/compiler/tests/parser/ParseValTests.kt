package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.parser.parseFunctionStatement
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isMap
import org.shedlang.compiler.tests.isSequence

class ParseValTests {
    @Test
    fun valIsValidFunctionStatement() {
        val source = "val x = 4;"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isVal(
            target = isValTargetVariable(name = isIdentifier("x")),
            expression = isIntLiteral(4)
        ))
    }

    @Test
    fun valIsValidModuleStatement() {
        val source = "val x = 4;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isVal(
            target = isValTargetVariable(name = isIdentifier("x")),
            expression = isIntLiteral(4)
        ))
    }

    @Test
    fun valTargetCanBeTuple() {
        val source = "val #(x, y) = z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isVal(
            target = isValTargetTuple(
                elements = isSequence(
                    isValTargetVariable(name = isIdentifier("x")),
                    isValTargetVariable(name = isIdentifier("y"))
                )
            )
        ))
    }

    @Test
    fun targetTupleCanHaveTrailingComma() {
        val source = "val #(x,) = z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isVal(
            target = isValTargetTuple(
                elements = isSequence(
                    isValTargetVariable(name = isIdentifier("x"))
                )
            )
        ))
    }

    @Test
    fun valTargetCanBeFields() {
        val source = "val @(.x as targetX, .y as targetY) = z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isVal(
            target = isValTargetFields(
                fields = isMap(
                    Identifier("x") to isValTargetVariable(name = isIdentifier("targetX")),
                    Identifier("y") to isValTargetVariable(name = isIdentifier("targetY"))
                )
            )
        ))
    }

    @Test
    fun valTargetFieldsCanHaveTrailingComma() {
        val source = "val @(.x as targetX,) = z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isVal(
            target = isValTargetFields(
                fields = isMap(
                    Identifier("x") to isValTargetVariable(name = isIdentifier("targetX"))
                )
            )
        ))
    }
}
