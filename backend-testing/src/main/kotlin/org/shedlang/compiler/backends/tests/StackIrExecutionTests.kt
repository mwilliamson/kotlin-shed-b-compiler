package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.Types
import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.UnaryOperator
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

interface StackIrExecutionEnvironment {
    fun evaluateExpression(node: ExpressionNode, type: Type, types: Types): IrValue
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
    fun stringLiteralIsEvaluatedToString() {
        val node = literalString("hello")

        val value = evaluateExpression(node, type = StringType)

        assertThat(value, isString("hello"))
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

    @Test
    fun minusOperatorNegatesOperand() {
        val node = unaryOperation(UnaryOperator.MINUS, literalInt(42))

        val value = evaluateExpression(node, type = IntType)

        assertThat(value, isInt(-42))
    }

    @Test
    fun whenOperandsAreEqualThenBooleanEqualityEvaluatesToTrue() {
        val left = literalBool(true)
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalBool(true))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to BoolType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenOperandsAreNotEqualThenBooleanEqualityEvaluatesToFalse() {
        val left = literalBool(true)
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalBool(false))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to BoolType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    private fun evaluateExpression(node: ExpressionNode, type: Type, types: Types = createTypes()) =
        environment.evaluateExpression(node, type, types = types)

    private fun isBool(expected: Boolean): Matcher<IrValue> {
        return cast(has(IrBool::value, equalTo(expected)))
    }

    private fun isCodePoint(expected: Int): Matcher<IrValue> {
        return cast(has(IrCodePoint::value, equalTo(expected)))
    }

    private fun isInt(expected: Int): Matcher<IrValue> {
        return cast(has(IrInt::value, equalTo(expected.toBigInteger())))
    }

    private fun isString(expected: String): Matcher<IrValue> {
        return cast(has(IrString::value, equalTo(expected)))
    }

    private val isUnit = isA<IrUnit>()

    private fun createTypes(
        expressionTypes: Map<Int, Type> = mapOf(),
        targetTypes: Map<Int, Type> = mapOf()
    ): Types {
        return TypesMap(
            discriminators = mapOf(),
            expressionTypes = expressionTypes,
            targetTypes = targetTypes,
            variableTypes = mapOf()
        )
    }
}
