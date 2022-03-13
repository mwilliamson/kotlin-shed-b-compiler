package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheckBlock

class TypeCheckBlockTests {
    @Test
    fun emptyBlockHasUnitType() {
        val block = block(statements = listOf())
        val context = typeContext()

        val result = typeCheckBlock(block, context)

        assertThat(result, isUnitType)
    }

    @Test
    fun blockHasTypeOfLastStatement() {
        val block = block(statements = listOf(
            expressionStatementNoReturn(literalInt()),
            expressionStatementReturn(literalString()),
        ))
        val context = typeContext()

        val result = typeCheckBlock(block, context)

        assertThat(result, isStringType)
    }
}
