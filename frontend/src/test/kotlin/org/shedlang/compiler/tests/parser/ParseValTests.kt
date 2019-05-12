package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.IntegerLiteralNode
import org.shedlang.compiler.parser.parseFunctionStatement
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier
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
}
