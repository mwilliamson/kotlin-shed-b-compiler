package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.UnaryOperator
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

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

    @Test
    fun integerLiteralIsEvaluatedToInteger() {
        val node = literalInt(42)

        val value = evaluateExpression(node, type = IntType)

        assertThat(value, isInt(42))
    }

    @Test
    fun unitLiteralIsEvaluatedToUnit() {
        val node = literalUnit()

        val value = evaluateExpression(node, type = UnitType)

        assertThat(value, isUnit)
    }

    @Test
    fun notOperatorNegatesOperand() {
        assertNotOperation(true, isBool(false))
        assertNotOperation(false, isBool(true))
    }

    private fun assertNotOperation(operand: Boolean, expected: Matcher<IrValue>) {
        val node = unaryOperation(UnaryOperator.NOT, literalBool(operand))

        val value = evaluateExpression(node, type = BoolType)

        assertThat(value, expected)
    }


    private fun evaluateExpression(node: ExpressionNode, type: Type) =
        environment.evaluateExpression(node, type)

    private fun isBool(expected: Boolean): Matcher<IrValue> {
        return cast(has(IrBool::value, equalTo(expected)))
    }

    private fun isCodePoint(expected: Int): Matcher<IrValue> {
        return cast(has(IrCodePoint::value, equalTo(expected)))
    }

    private fun isInt(expected: Int): Matcher<IrValue> {
        return cast(has(IrInt::value, equalTo(expected.toBigInteger())))
    }

    private val isUnit = isA<IrUnit>()
}
