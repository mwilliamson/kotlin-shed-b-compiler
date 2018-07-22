package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.*

class InterpreterTests {
    @Test
    fun unitNodeEvaluatesToUnitValue() {
        assertThat(evaluate(literalUnit()), isPureResult(equalTo(UnitValue)))
    }

    @Test
    fun booleanNodeEvaluatesToBooleanValue() {
        assertThat(evaluate(literalBool(true)), isPureResult(equalTo(BooleanValue(true))))
        assertThat(evaluate(literalBool(false)), isPureResult(equalTo(BooleanValue(false))))
    }

    @Test
    fun integerNodeEvaluatesToIntegerValue() {
        assertThat(evaluate(literalInt(42)), isPureResult(equalTo(IntegerValue(42))))
    }

    @Test
    fun stringNodeEvaluatesToStringValue() {
        assertThat(evaluate(literalString("hello")), isPureResult(equalTo(StringValue("hello"))))
    }

    @Test
    fun characterNodeEvaluatesToCharacterValue() {
        assertThat(evaluate(literalChar('!')), isPureResult(equalTo(CharacterValue('!'.toInt()))))
    }

    @Test
    fun symbolNodeEvaluatesToSymbolValue() {
        assertThat(evaluate(symbolName("@cons")), isPureResult(equalTo(SymbolValue("@cons"))))
    }

    @Test
    fun variableReferenceEvaluatesToValueOfVariable() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(42),
                "y" to IntegerValue(47)
            ))
        )
        val value = evaluate(variableReference("x"), context)
        assertThat(value, isPureResult(equalTo(IntegerValue(42))))
    }

    @Test
    fun variablesCanBeOverriddenInInnerScope() {
        val context = createContext(
            scope = Scope(listOf(
                ScopeFrame(mapOf(
                    "x" to IntegerValue(47)
                )),
                ScopeFrame(mapOf(
                    "x" to IntegerValue(42)
                ))
            ))
        )
        val value = evaluate(variableReference("x"), context)
        assertThat(value, isPureResult(equalTo(IntegerValue(47))))
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
        assertThat(value, isPureResult(equalTo(module)))
    }

    @Test
    fun equalityOfIntegers() {
        val equalValue = evaluate(binaryOperation(Operator.EQUALS, literalInt(42), literalInt(42)))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))


        val notEqualValue = evaluate(binaryOperation(Operator.EQUALS, literalInt(42), literalInt(47)))
        assertThat(notEqualValue, isPureResult(equalTo(BooleanValue(false))))
    }

    @Test
    fun additionOfIntegersEvaluatesToTotalValue() {
        val value = evaluate(binaryOperation(Operator.ADD, literalInt(1), literalInt(2)))
        assertThat(value, isPureResult(equalTo(IntegerValue(3))))
    }

    @Test
    fun subtractionOfIntegersEvaluatesToDifference() {
        val value = evaluate(binaryOperation(Operator.SUBTRACT, literalInt(1), literalInt(2)))
        assertThat(value, isPureResult(equalTo(IntegerValue(-1))))
    }

    @Test
    fun multiplicationOfIntegersEvaluatesToDifference() {
        val value = evaluate(binaryOperation(Operator.MULTIPLY, literalInt(2), literalInt(3)))
        assertThat(value, isPureResult(equalTo(IntegerValue(6))))
    }

    @Test
    fun equalityOfStrings() {
        val equalValue = evaluate(binaryOperation(Operator.EQUALS, literalString("a"), literalString("a")))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))


        val notEqualValue = evaluate(binaryOperation(Operator.EQUALS, literalString("a"), literalString("b")))
        assertThat(notEqualValue, isPureResult(equalTo(BooleanValue(false))))
    }

    @Test
    fun additionOfStringsEvaluatesToConcatenation() {
        val value = evaluate(binaryOperation(Operator.ADD, literalString("hello "), literalString("world")))
        assertThat(value, isPureResult(equalTo(StringValue("hello world"))))
    }

    @Test
    fun binaryOperationLeftOperandIsEvaluatedBeforeRightOperand() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(1),
                "y" to IntegerValue(2)
            ))
        )
        val expression = BinaryOperation(
            Operator.ADD,
            VariableReference("x"),
            VariableReference("y")
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            VariableReference("y")
        ))))
    }

    @Test
    fun binaryOperationRightOperandIsEvaluatedWhenLeftOperandIsValue() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "y" to IntegerValue(2)
            ))
        )
        val expression = BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            VariableReference("y")
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            IntegerValue(2)
        ))))
    }

    @Test
    fun callReceiverIsEvaluatedFirst() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(1)
            ))
        )
        val expression = call(receiver = VariableReference("x")).evaluate(context)
        assertThat(expression, isPureResult(equalTo(call(
            receiver = IntegerValue(1)
        ))))
    }

    @Test
    fun callPositionalArgumentsAreEvaluatedInOrder() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "y" to IntegerValue(2),
                "z" to IntegerValue(3)
            ))
        )
        val expression = Call(
            PrintValue,
            positionalArgumentExpressions = listOf(
                VariableReference("y"),
                VariableReference("z")
            ),
            positionalArgumentValues = listOf(
                IntegerValue(1)
            )
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(Call(
            PrintValue,
            positionalArgumentExpressions = listOf(
                IntegerValue(2),
                VariableReference("z")
            ),
            positionalArgumentValues = listOf(
                IntegerValue(1)
            )
        ))))
    }

    @Test
    fun callPositionalArgumentsAreMovedToValuesOnceFullyEvaluated() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "y" to IntegerValue(2),
                "z" to IntegerValue(3)
            ))
        )
        val expression = Call(
            PrintValue,
            positionalArgumentExpressions = listOf(
                IntegerValue(2),
                VariableReference("z")
            ),
            positionalArgumentValues = listOf(
                IntegerValue(1)
            )
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(Call(
            PrintValue,
            positionalArgumentExpressions = listOf(
                VariableReference("z")
            ),
            positionalArgumentValues = listOf(
                IntegerValue(1),
                IntegerValue(2)
            )
        ))))
    }

    @Test
    fun callingPrintUpdatesStdout() {
        val context = createContext()
        val result = call(
            PrintValue,
            positionalArgumentValues = listOf(StringValue("hello"))
        ).evaluate(context)
        assertThat(result.stdout, equalTo("hello"))
    }

    @Test
    fun callingIntToStringConvertsIntToString() {
        val context = createContext()
        val result = call(
            IntToStringValue,
            positionalArgumentValues = listOf(IntegerValue(42))
        ).evaluate(context)
        assertThat(result, isPureResult(equalTo(StringValue("42"))))
    }

    @Test
    fun whenReceiverIsFunctionThenCallIsEvaluatedToPartiallyEvaluatedFunction() {
        val context = createContext()
        val function = FunctionValue(
            positionalParameterNames = listOf(),
            body = listOf(
                ExpressionStatement(IntegerValue(1), isReturn = false)
            ),
            module = lazy { ModuleValue(mapOf()) }
        )
        val expression = call(
            receiver = function,
            positionalArgumentValues = listOf()
        ).evaluate(context)
        assertThat(expression, isPureResult(isBlock(
            body = equalTo(listOf(
                ExpressionStatement(IntegerValue(1), isReturn = false)
            ))
        )))
    }

    @Test
    fun whenCallingFunctionThenBlockHasScopeFromFunction() {
        val context = createContext()
        val function = FunctionValue(
            positionalParameterNames = listOf("arg0", "arg1"),
            body = listOf(),
            module = lazy { ModuleValue(mapOf(
                Identifier("x") to IntegerValue(42)
            )) }
        )
        val expression = call(
            receiver = function,
            positionalArgumentValues = listOf(StringValue("zero"), StringValue("one"))
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(Block(
            body = listOf(),
            scope = Scope(listOf(
                ScopeFrame(mapOf(
                    "arg0" to StringValue("zero"),
                    "arg1" to StringValue("one")
                )),
                ScopeFrame(mapOf(
                    "x" to IntegerValue(42)
                )),
                builtinStackFrame
            ))
        ))))
    }

    @Test
    fun fieldAccessReceiverIsEvaluatedFirst() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(1)
            ))
        )
        val expression = FieldAccess(VariableReference("x"), Identifier("y")).evaluate(context)
        assertThat(expression, isPureResult(equalTo(FieldAccess(
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
        assertThat(expression, isPureResult(equalTo(IntegerValue(42))))
    }

    @Test
    fun whenConditionalBranchesIsEmptyThenIfEvaluatesToElseBranch() {
        val ifExpression = If(
            conditionalBranches = listOf(),
            elseBranch = listOf(returns(IntegerValue(3)))
        )
        val expression = ifExpression.evaluate(createContext())
        assertThat(expression, isPureResult(isBlock(
            body = equalTo(listOf(returns(IntegerValue(3))))
        )))
    }

    @Test
    fun whenConditionalBranchConditionIsFalseValueThenConditionalBranchIsRemoved() {
        val ifExpression = If(
            conditionalBranches = listOf(
                ConditionalBranch(BooleanValue(false), listOf(returns(IntegerValue(1)))),
                ConditionalBranch(VariableReference("y"), listOf(returns(IntegerValue(2))))
            ),
            elseBranch = listOf(returns(IntegerValue(3)))
        )
        val expression = ifExpression.evaluate(createContext())
        assertThat(expression, isPureResult(equalTo(If(
            conditionalBranches = listOf(
                ConditionalBranch(VariableReference("y"), listOf(returns(IntegerValue(2))))
            ),
            elseBranch = listOf(returns(IntegerValue(3)))
        ))))
    }

    @Test
    fun whenConditionalBranchConditionIsTrueValueThenIfEvaluatesToBranchBody() {
        val ifExpression = If(
            conditionalBranches = listOf(
                ConditionalBranch(BooleanValue(true), listOf(returns(IntegerValue(1)))),
                ConditionalBranch(VariableReference("y"), listOf(returns(IntegerValue(2))))
            ),
            elseBranch = listOf(returns(IntegerValue(3)))
        )
        val expression = ifExpression.evaluate(createContext())
        assertThat(expression, isPureResult(isBlock(
            body = equalTo(listOf(returns(IntegerValue(1))))
        )))
    }

    @Test
    fun whenConditionalBranchConditionIsNotValueThenConditionIsEvaluated() {
        val ifExpression = If(
            conditionalBranches = listOf(
                ConditionalBranch(VariableReference("x"), listOf(returns(IntegerValue(1)))),
                ConditionalBranch(VariableReference("y"), listOf(returns(IntegerValue(2))))
            ),
            elseBranch = listOf(returns(IntegerValue(3)))
        )
        val context = createContext(scope = scopeOf(mapOf("x" to BooleanValue(true))))
        val expression = ifExpression.evaluate(context)
        assertThat(expression, isPureResult(equalTo(If(
            conditionalBranches = listOf(
                ConditionalBranch(BooleanValue(true), listOf(returns(IntegerValue(1)))),
                ConditionalBranch(VariableReference("y"), listOf(returns(IntegerValue(2))))
            ),
            elseBranch = listOf(returns(IntegerValue(3)))
        ))))
    }

    @Test
    fun whenBlockHasNoStatementsThenValueIsUnit() {
        val context = createContext()
        val expression = Block(
            body = listOf(),
            scope = Scope(listOf())
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(UnitValue)))
    }

    @Test
    fun whenBlockHasStatementThenStatementIsEvaluatedInScope() {
        val context = createContext()
        val expression = Block(
            body = listOf(
                ExpressionStatement(expression = VariableReference("x"), isReturn = false),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            ),
            scope = scopeOf(mapOf(
                "x" to IntegerValue(42)
            ))
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(Block(
            body = listOf(
                ExpressionStatement(IntegerValue(42), isReturn = false),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            ),
            scope = scopeOf(mapOf(
                "x" to IntegerValue(42)
            ))
        ))))
    }

    @Test
    fun whenBlockHasNonReturningExpressionStatementWithValueThenStatementIsDropped() {
        val context = createContext()
        val expression = Block(
            body = listOf(
                ExpressionStatement(IntegerValue(42), isReturn = false),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            ),
            scope = Scope(listOf())
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(Block(
            body = listOf(
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            ),
            scope = Scope(listOf())
        ))))
    }

    @Test
    fun whenBlockHasReturningExpressionStatementWithValueThenValueIsExpressionStatementValue() {
        val context = createContext()
        val expression = Block(
            body = listOf(
                ExpressionStatement(IntegerValue(42), isReturn = true),
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            ),
            scope = Scope(listOf())
        ).evaluate(context)
        assertThat(expression, isPureResult(equalTo(IntegerValue(42))))
    }

    private fun evaluate(expression: ExpressionNode): EvaluationResult<InterpreterValue> {
        return evaluate(expression, createContext())
    }

    private fun createContext(
        scope: Scope = Scope(listOf()),
        modules: Map<List<Identifier>, ModuleValue> = mapOf()
    ): InterpreterContext {
        return InterpreterContext(
            scope = scope,
            modules = modules
        )
    }

    private fun scopeOf(variables: Map<String, InterpreterValue>): Scope {
        return Scope(listOf(ScopeFrame(variables)))
    }

    private fun call(
        receiver: Expression,
        positionalArgumentExpressions: List<Expression> = listOf(),
        positionalArgumentValues: List<InterpreterValue> = listOf()
    ): Call {
        return Call(
            receiver,
            positionalArgumentExpressions = positionalArgumentExpressions,
            positionalArgumentValues = positionalArgumentValues
        )
    }

    private fun returns(expression: IntegerValue) = ExpressionStatement(expression, isReturn = true)

    private inline fun <T: Any, reified U: T> isPureResult(matcher: Matcher<U>): Matcher<EvaluationResult<T>> {
        return has(EvaluationResult<T>::value, cast(matcher))
    }

    private fun isBlock(body: Matcher<List<Statement>>): Matcher<Block> {
        return has(Block::body, body)
    }
}
