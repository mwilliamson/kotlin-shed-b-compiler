package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.*

class InterpreterTests {
    @Test
    fun unitNodeEvaluatesToUnitValue() {
        assertThat(evaluate(literalUnit()), cast(equalTo(UnitValue)))
    }

    @Test
    fun booleanNodeEvaluatesToBooleanValue() {
        assertThat(evaluate(literalBool(true)), cast(equalTo(BooleanValue(true))))
        assertThat(evaluate(literalBool(false)), cast(equalTo(BooleanValue(false))))
    }

    @Test
    fun integerNodeEvaluatesToIntegerValue() {
        assertThat(evaluate(literalInt(42)), cast(equalTo(IntegerValue(42))))
    }

    @Test
    fun stringNodeEvaluatesToStringValue() {
        assertThat(evaluate(literalString("hello")), cast(equalTo(StringValue("hello"))))
    }

    @Test
    fun characterNodeEvaluatesToCharacterValue() {
        assertThat(evaluate(literalChar('!')), cast(equalTo(CharacterValue('!'.toInt()))))
    }

    @Test
    fun symbolNodeEvaluatesToSymbolValue() {
        assertThat(evaluate(symbolName("@cons")), cast(equalTo(SymbolValue("@cons"))))
    }

    @Test
    fun variableReferenceEvaluatesToValueOfVariable() {
        val context = createContext(
            variables = mapOf(
                "x" to IntegerValue(42),
                "y" to IntegerValue(47)
            )
        )
        val value = evaluate(variableReference("x"), context)
        assertThat(value, cast(equalTo(IntegerValue(42))))
    }

    private fun evaluate(expression: ExpressionNode): InterpreterValue {
        return evaluate(expression, createContext())
    }

    private fun createContext(variables: Map<String, InterpreterValue> = mapOf()): InterpreterContext {
        return InterpreterContext(variables)
    }
}
