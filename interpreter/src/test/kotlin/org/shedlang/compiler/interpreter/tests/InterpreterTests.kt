package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.EMPTY_TYPES
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.*

class InterpreterTests {
    @Test
    fun unitNodeEvaluatesToUnitValue() {
        assertThat(fullyEvaluate(literalUnit()), isPureResult(equalTo(UnitValue)))
    }

    @Test
    fun booleanNodeEvaluatesToBooleanValue() {
        assertThat(fullyEvaluate(literalBool(true)), isPureResult(equalTo(BooleanValue(true))))
        assertThat(fullyEvaluate(literalBool(false)), isPureResult(equalTo(BooleanValue(false))))
    }

    @Test
    fun integerNodeEvaluatesToIntegerValue() {
        assertThat(fullyEvaluate(literalInt(42)), isPureResult(equalTo(IntegerValue(42))))
    }

    @Test
    fun stringNodeEvaluatesToStringValue() {
        assertThat(fullyEvaluate(literalString("hello")), isPureResult(equalTo(StringValue("hello"))))
    }

    @Test
    fun characterNodeEvaluatesToCharacterValue() {
        assertThat(fullyEvaluate(literalCodePoint('!')), isPureResult(equalTo(CharacterValue('!'.toInt()))))
    }

    @Test
    fun symbolNodeEvaluatesToSymbolValue() {
        assertThat(fullyEvaluate(symbolName("@cons")), isPureResult(equalTo(symbolValue(listOf(), "@cons"))))
    }

    @Test
    fun variableReferenceEvaluatesToValueOfVariable() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(42),
                "y" to IntegerValue(47)
            ))
        )
        val value = fullyEvaluate(variableReference("x"), context)
        assertThat(value, isPureResult(equalTo(IntegerValue(42))))
    }

    @Test
    fun binaryOperationIsEvaluated() {
        val equalValue = fullyEvaluate(binaryOperation(BinaryOperator.EQUALS, literalInt(42), literalInt(42)))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))
    }

    private fun fullyEvaluate(
        expression: ExpressionNode,
        context: InterpreterContext = createContext()
    ): EvaluationResult<InterpreterValue> {
        return fullyEvaluate(
            loadExpression(expression, LoaderContext(moduleName = listOf(), types = EMPTY_TYPES)),
            context
        )
    }
}
