package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator
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

    @Test
    fun moduleReferenceEvaluatesToModuleValue() {
        val module = ModuleValue(fields = mapOf())
        val context = createContext(
            modules = mapOf(
                listOf(Identifier("X")) to module
            )
        )
        val value = evaluate(ModuleReference(listOf(Identifier("X"))), context)
        assertThat(value, cast(equalTo(module)))
    }

    @Test
    fun equalityOfIntegers() {
        val equalValue = evaluate(binaryOperation(Operator.EQUALS, literalInt(42), literalInt(42)))
        assertThat(equalValue, cast(equalTo(BooleanValue(true))))


        val notEqualValue = evaluate(binaryOperation(Operator.EQUALS, literalInt(42), literalInt(47)))
        assertThat(notEqualValue, cast(equalTo(BooleanValue(false))))
    }

    @Test
    fun additionOfIntegersEvaluatesToTotalValue() {
        val value = evaluate(binaryOperation(Operator.ADD, literalInt(1), literalInt(2)))
        assertThat(value, cast(equalTo(IntegerValue(3))))
    }

    @Test
    fun subtractionOfIntegersEvaluatesToDifference() {
        val value = evaluate(binaryOperation(Operator.SUBTRACT, literalInt(1), literalInt(2)))
        assertThat(value, cast(equalTo(IntegerValue(-1))))
    }

    @Test
    fun multiplicationOfIntegersEvaluatesToDifference() {
        val value = evaluate(binaryOperation(Operator.MULTIPLY, literalInt(2), literalInt(3)))
        assertThat(value, cast(equalTo(IntegerValue(6))))
    }

    @Test
    fun equalityOfStrings() {
        val equalValue = evaluate(binaryOperation(Operator.EQUALS, literalString("a"), literalString("a")))
        assertThat(equalValue, cast(equalTo(BooleanValue(true))))


        val notEqualValue = evaluate(binaryOperation(Operator.EQUALS, literalString("a"), literalString("b")))
        assertThat(notEqualValue, cast(equalTo(BooleanValue(false))))
    }

    @Test
    fun additionOfStringsEvaluatesToConcatenation() {
        val value = evaluate(binaryOperation(Operator.ADD, literalString("hello "), literalString("world")))
        assertThat(value, cast(equalTo(StringValue("hello world"))))
    }

    @Test
    fun binaryOperationLeftOperandIsEvaluatedBeforeRightOperand() {
        val context = createContext(
            variables = mapOf(
                "x" to IntegerValue(1),
                "y" to IntegerValue(2)
            )
        )
        val expression = BinaryOperation(
            Operator.ADD,
            VariableReference("x"),
            VariableReference("y")
        ).evaluate(context)
        assertThat(expression, cast(equalTo(BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            VariableReference("y")
        ))))
    }

    @Test
    fun binaryOperationRightOperandIsEvaluatedWhenLeftOperandIsValue() {
        val context = createContext(
            variables = mapOf(
                "y" to IntegerValue(2)
            )
        )
        val expression = BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            VariableReference("y")
        ).evaluate(context)
        assertThat(expression, cast(equalTo(BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            IntegerValue(2)
        ))))
    }

    @Test
    fun callReceiverIsEvaluatedFirst() {
        val context = createContext(
            variables = mapOf(
                "x" to IntegerValue(1)
            )
        )
        val expression = Call(VariableReference("x"), listOf()).evaluate(context)
        assertThat(expression, cast(equalTo(Call(
            IntegerValue(1),
            listOf()
        ))))
    }

    @Test
    fun callingPrintUpdatesStdout() {
        val context = createContext()
        Call(
            PrintValue,
            listOf(StringValue("hello"))
        ).evaluate(context)
        assertThat(context.stdout, equalTo("hello"))
    }

    @Test
    fun whenReceiverIsFunctionThenCallIsEvaluatedToPartiallyEvaluatedFunction() {
        val context = createContext()
        val function = FunctionValue(
            body = listOf(
                ExpressionStatement(IntegerValue(1), isReturn = false)
            )
        )
        val expression = Call(function, listOf()).evaluate(context)
        assertThat(expression, cast(equalTo(PartiallyEvaluatedFunction(
            body = listOf(
                ExpressionStatement(IntegerValue(1), isReturn = false)
            )
        ))))
    }

    @Test
    fun fieldAccessReceiverIsEvaluatedFirst() {
        val context = createContext(
            variables = mapOf(
                "x" to IntegerValue(1)
            )
        )
        val expression = FieldAccess(VariableReference("x"), Identifier("y")).evaluate(context)
        assertThat(expression, cast(equalTo(FieldAccess(
            IntegerValue(1),
            Identifier("y")
        ))))
    }

    @Test
    fun whenReceiverIsModuleThenFieldAccessIsEvaluatedToModuleFieldValue() {
        val context = createContext()
        val module = ModuleValue(
            fields = mapOf(Identifier("x") to IntegerValue(42))
        )
        val expression = FieldAccess(module, Identifier("x")).evaluate(context)
        assertThat(expression, cast(equalTo(IntegerValue(42))))
    }

    @Test
    fun whenPartiallyEvaluatedFunctionHasNoStatementsThenValueIsUnit() {
        val context = createContext()
        val expression = PartiallyEvaluatedFunction(
            body = listOf()
        ).evaluate(context)
        assertThat(expression, cast(equalTo(UnitValue)))
    }

    @Test
    fun whenPartiallyEvaluatedFunctionHasStatementThenStatementIsEvaluated() {
        val context = createContext(
            variables = mapOf(
                "x" to IntegerValue(42)
            )
        )
        val expression = PartiallyEvaluatedFunction(
            body = listOf(
                ExpressionStatement(expression = VariableReference("x"), isReturn = false),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            )
        ).evaluate(context)
        assertThat(expression, cast(equalTo(PartiallyEvaluatedFunction(
            body = listOf(
                ExpressionStatement(IntegerValue(42), isReturn = false),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            )
        ))))
    }

    @Test
    fun whenPartiallyEvaluatedFunctionHasNonReturningExpressionStatementWithValueThenStatementIsDropped() {
        val context = createContext()
        val expression = PartiallyEvaluatedFunction(
            body = listOf(
                ExpressionStatement(IntegerValue(42), isReturn = false),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            )
        ).evaluate(context)
        assertThat(expression, cast(equalTo(PartiallyEvaluatedFunction(
            body = listOf(
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            )
        ))))
    }

    @Test
    fun whenPartiallyEvaluatedFunctionHasReturningExpressionStatementWithValueThenValueIsExpressionStatementValue() {
        val context = createContext()
        val expression = PartiallyEvaluatedFunction(
            body = listOf(
                ExpressionStatement(IntegerValue(42), isReturn = true),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            )
        ).evaluate(context)
        assertThat(expression, cast(equalTo(IntegerValue(42))))
    }

    private fun evaluate(expression: ExpressionNode): InterpreterValue {
        return evaluate(expression, createContext())
    }

    private fun createContext(
        variables: Map<String, InterpreterValue> = mapOf(),
        modules: Map<List<Identifier>, ModuleValue> = mapOf()
    ): InterpreterContext {
        return InterpreterContext(
            variables = variables,
            modules = modules
        )
    }
}
