package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.allOf

class EvaluateVariableReferenceTests {
    @Test
    fun variableReferenceEvaluatesToValueOfVariable() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(42),
                "y" to IntegerValue(47)
            ))
        )
        val value = evaluate(VariableReference("x"), context)
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
        val value = evaluate(VariableReference("x"), context)
        assertThat(value, isPureResult(equalTo(IntegerValue(47))))
    }
}

class EvaluateModuleReferenceTests {
    @Test
    fun moduleReferenceEvaluatesToModuleValue() {
        val module = ModuleValue(fields = mapOf())
        val context = createContext(
            moduleValues = mapOf(
                listOf(Identifier("X")) to module
            )
        )
        val value = ModuleReference(listOf(Identifier("X"))).evaluate(context)
        assertThat(value, isPureResult(equalTo(module)))
    }

    @Test
    fun moduleReferenceEvaluatesModuleWhenModuleIsNotValue() {
        val module = ModuleExpression(
            fieldExpressions = listOf(),
            fieldValues = listOf()
        )
        val context = createContext(
            moduleValues = mapOf(),
            moduleExpressions = mapOf(
                listOf(Identifier("X")) to module
            )
        )
        val value = ModuleReference(listOf(Identifier("X"))).evaluate(context)
        assertThat(value, cast(allOf(
            has(EvaluationResult.ModuleValueUpdate::moduleName, equalTo(listOf(Identifier("X")))),
            has(EvaluationResult.ModuleValueUpdate::value, equalTo(ModuleValue(mapOf())))
        )))
    }
}

class EvaluateBinaryOperationTests {
    @Test
    fun equalityOfIntegers() {
        val equalValue = evaluate(BinaryOperation(Operator.EQUALS, IntegerValue(42), IntegerValue(42)))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))


        val notEqualValue = evaluate(BinaryOperation(Operator.EQUALS, IntegerValue(42), IntegerValue(47)))
        assertThat(notEqualValue, isPureResult(equalTo(BooleanValue(false))))
    }

    @Test
    fun additionOfIntegersEvaluatesToTotalValue() {
        val value = evaluate(BinaryOperation(Operator.ADD, IntegerValue(1), IntegerValue(2)))
        assertThat(value, isPureResult(equalTo(IntegerValue(3))))
    }

    @Test
    fun subtractionOfIntegersEvaluatesToDifference() {
        val value = evaluate(BinaryOperation(Operator.SUBTRACT, IntegerValue(1), IntegerValue(2)))
        assertThat(value, isPureResult(equalTo(IntegerValue(-1))))
    }

    @Test
    fun multiplicationOfIntegersEvaluatesToDifference() {
        val value = evaluate(BinaryOperation(Operator.MULTIPLY, IntegerValue(2), IntegerValue(3)))
        assertThat(value, isPureResult(equalTo(IntegerValue(6))))
    }

    @Test
    fun equalityOfStrings() {
        val equalValue = evaluate(BinaryOperation(Operator.EQUALS, StringValue("a"), StringValue("a")))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))


        val notEqualValue = evaluate(BinaryOperation(Operator.EQUALS, StringValue("a"), StringValue("b")))
        assertThat(notEqualValue, isPureResult(equalTo(BooleanValue(false))))
    }

    @Test
    fun additionOfStringsEvaluatesToConcatenation() {
        val value = evaluate(BinaryOperation(Operator.ADD, StringValue("hello "), StringValue("world")))
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
        val expression = evaluate(BinaryOperation(
            Operator.ADD,
            VariableReference("x"),
            VariableReference("y")
        ), context)
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
        val expression = evaluate(BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            VariableReference("y")
        ), context)
        assertThat(expression, isPureResult(equalTo(BinaryOperation(
            Operator.ADD,
            IntegerValue(1),
            IntegerValue(2)
        ))))
    }
}

class EvaluateCallTests {
    @Test
    fun callReceiverIsEvaluatedFirst() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(1)
            ))
        )
        val expression = evaluate(call(receiver = VariableReference("x")), context)
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
        val expression = evaluate(
            Call(
                PrintValue,
                positionalArgumentExpressions = listOf(
                    VariableReference("y"),
                    VariableReference("z")
                ),
                positionalArgumentValues = listOf(
                    IntegerValue(1)
                )
            ),
            context
        )
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
        val expression = evaluate(
            Call(
                PrintValue,
                positionalArgumentExpressions = listOf(
                    IntegerValue(2),
                    VariableReference("z")
                ),
                positionalArgumentValues = listOf(
                    IntegerValue(1)
                )
            ),
            context
        )
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
        val result = evaluate(
            call(
                PrintValue,
                positionalArgumentValues = listOf(StringValue("hello"))
            ),
            context
        ) as EvaluationResult.Value
        assertThat(result.stdout, equalTo("hello"))
    }

    @Test
    fun callingIntToStringConvertsIntToString() {
        val context = createContext()
        val result = evaluate(
            call(
                IntToStringValue,
                positionalArgumentValues = listOf(IntegerValue(42))
            ),
            context
        )
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
            moduleName = null
        )
        val expression = evaluate(
            call(
                receiver = function,
                positionalArgumentValues = listOf()
            ),
            context
        )
        assertThat(expression, isPureResult(isBlock(
            body = equalTo(listOf(
                ExpressionStatement(IntegerValue(1), isReturn = false)
            ))
        )))
    }

    @Test
    fun whenCallingFunctionThenBlockHasScopeFromFunction() {
        val context = createContext(
            moduleValues = mapOf(
                listOf(Identifier("Some"), Identifier("Module")) to ModuleValue(mapOf(
                    Identifier("x") to IntegerValue(42)
                ))
            )
        )
        val function = FunctionValue(
            positionalParameterNames = listOf("arg0", "arg1"),
            body = listOf(),
            moduleName = listOf(Identifier("Some"), Identifier("Module"))
        )
        val expression = evaluate(
            call(
                receiver = function,
                positionalArgumentValues = listOf(StringValue("zero"), StringValue("one"))
            ),
            context
        )
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
                ScopeFrame(mapOf(
                    "moduleName" to StringValue("Some.Module")
                )),
                builtinStackFrame
            ))
        ))))
    }
}

class EvaluateFieldAccessTests {
    @Test
    fun fieldAccessReceiverIsEvaluatedFirst() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "x" to IntegerValue(1)
            ))
        )
        val expression = evaluate(FieldAccess(VariableReference("x"), Identifier("y")), context)
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
        val expression = evaluate(FieldAccess(module, Identifier("x")), context)
        assertThat(expression, isPureResult(equalTo(IntegerValue(42))))
    }
}

class EvaluateIfTests {
    @Test
    fun whenConditionalBranchesIsEmptyThenIfEvaluatesToElseBranch() {
        val ifExpression = If(
            conditionalBranches = listOf(),
            elseBranch = listOf(returns(IntegerValue(3)))
        )
        val expression = evaluate(ifExpression)
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
        val expression = evaluate(ifExpression)
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
        val expression = evaluate(ifExpression)
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
        val expression = evaluate(ifExpression, context)
        assertThat(expression, isPureResult(equalTo(If(
            conditionalBranches = listOf(
                ConditionalBranch(BooleanValue(true), listOf(returns(IntegerValue(1)))),
                ConditionalBranch(VariableReference("y"), listOf(returns(IntegerValue(2))))
            ),
            elseBranch = listOf(returns(IntegerValue(3)))
        ))))
    }
}

class EvaluateBlockTests {
    @Test
    fun whenBlockHasNoStatementsThenValueIsUnit() {
        val expression = evaluate(
            Block(
                body = listOf(),
                scope = Scope(listOf())
            )
        )
        assertThat(expression, isPureResult(equalTo(UnitValue)))
    }

    @Test
    fun whenBlockHasStatementThenStatementIsEvaluatedInScope() {
        val expression = evaluate(
            Block(
                body = listOf(
                    ExpressionStatement(expression = VariableReference("x"), isReturn = false),
                    ExpressionStatement(expression = VariableReference("y"), isReturn = false)
                ),
                scope = scopeOf(mapOf(
                    "x" to IntegerValue(42)
                ))
            )
        )
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
        val expression = evaluate(
            Block(
                body = listOf(
                    ExpressionStatement(IntegerValue(42), isReturn = false),
                    ExpressionStatement(expression = VariableReference("y"), isReturn = false)
                ),
                scope = Scope(listOf())
            )
        )
        assertThat(expression, isPureResult(equalTo(Block(
            body = listOf(
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            ),
            scope = Scope(listOf())
        ))))
    }

    @Test
    fun whenBlockHasReturningExpressionStatementWithValueThenValueIsExpressionStatementValue() {
        val expression = evaluate(
            Block(
                body = listOf(
                    ExpressionStatement(IntegerValue(42), isReturn = true),
                    ExpressionStatement(expression = VariableReference("y"), isReturn = false)
                ),
                scope = Scope(listOf())
            )
        )
        assertThat(expression, isPureResult(equalTo(IntegerValue(42))))
    }
}

private fun evaluate(
    expression: IncompleteExpression,
    context: InterpreterContext = createContext()
): EvaluationResult<Expression> {
    return expression.evaluate(context)
}
