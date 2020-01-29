package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.stackir.IrBool
import org.shedlang.compiler.stackir.IrCodePoint
import org.shedlang.compiler.stackir.IrValue
import org.shedlang.compiler.tests.literalBool
import org.shedlang.compiler.tests.literalCodePoint
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.CodePointType
import org.shedlang.compiler.types.Type

interface StackIrExecutionEnvironment {
    fun evaluateExpression(node: ExpressionNode, type: Type): IrValue
}

abstract class StackIrExecutionTests(private val environment: StackIrExecutionEnvironment) {
    @Test
    fun trueLiteralIsEvaluatedToTrue() {
        val node = literalBool(true)

        val value = evaluateExpression(node, type = BoolType)

        assertThat(value, isBool(true))
    }

    @Test
    fun falseLiteralIsEvaluatedToFalse() {
        val node = literalBool(false)

        val value = evaluateExpression(node, type = BoolType)

        assertThat(value, isBool(false))
    }

    @Test
    fun codePointLiteralIsEvaluatedToCodePoint() {
        val node = literalCodePoint('X')

        val value = evaluateExpression(node, type = CodePointType)

        assertThat(value, isCodePoint('X'.toInt()))
    }

    private fun evaluateExpression(node: ExpressionNode, type: Type) =
        environment.evaluateExpression(node, type)

    private fun isBool(expected: Boolean): Matcher<IrValue> {
        return cast(has(IrBool::value, equalTo(expected)))
    }

    private fun isCodePoint(expected: Int): Matcher<IrValue> {
        return cast(has(IrCodePoint::value, equalTo(expected)))
    }
}
