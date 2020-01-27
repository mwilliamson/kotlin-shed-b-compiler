package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.stackir.IrBool
import org.shedlang.compiler.stackir.IrValue
import org.shedlang.compiler.tests.literalBool

interface StackIrExecutionEnvironment {
    fun evaluateExpression(node: ExpressionNode): IrValue
}

abstract class StackIrExecutionTests(private val environment: StackIrExecutionEnvironment) {
    @Test
    fun trueLiteralIsEvaluatedToTrue() {
        val node = literalBool(true)

        val value = evaluateExpression(node)

        assertThat(value, isBool(true))
    }

    @Test
    fun falseLiteralIsEvaluatedToFalse() {
        val node = literalBool(false)

        val value = evaluateExpression(node)

        assertThat(value, isBool(false))
    }

    private fun evaluateExpression(node: ExpressionNode) =
        environment.evaluateExpression(node)

    private fun isBool(expected: Boolean): Matcher<IrValue> {
        return cast(has(IrBool::value, equalTo(expected)))
    }
}
