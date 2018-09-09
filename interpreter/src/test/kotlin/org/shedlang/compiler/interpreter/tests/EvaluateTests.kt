package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.AnyType
import org.shedlang.compiler.types.freshShapeId

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
                ScopeFrameMap(mapOf(
                    "x" to IntegerValue(47)
                )),
                ScopeFrameMap(mapOf(
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

    @Test
    fun expressionsAtTopLevelOfModuleAreEvaluatedInModuleScope() {
        val module = ModuleExpression(
            fieldExpressions = listOf(
                Identifier("y") to VariableReference("x")
            ),
            fieldValues = listOf(
                Identifier("x") to IntegerValue(1)
            )
        )
        val context = createContext(
            moduleValues = mapOf(),
            moduleExpressions = mapOf(
                listOf(Identifier("X")) to module
            )
        )
        val value = ModuleReference(listOf(Identifier("X"))).evaluate(context)
        assertThat(value, cast(allOf(
            has(EvaluationResult.ModuleExpressionUpdate::value, equalTo(ModuleExpression(
                fieldExpressions = listOf(
                    Identifier("y") to IntegerValue(1)
                ),
                fieldValues = listOf(
                    Identifier("x") to IntegerValue(1)
                )
            )))
        )))
    }
}

class EvaluateBinaryOperationTests {
    @Test
    fun equalityOfBooleans() {
        val equalValue = evaluate(BinaryOperation(Operator.EQUALS, BooleanValue(true), BooleanValue(true)))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))

        val notEqualValue = evaluate(BinaryOperation(Operator.EQUALS, BooleanValue(true), BooleanValue(false)))
        assertThat(notEqualValue, isPureResult(equalTo(BooleanValue(false))))
    }

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
    fun equalityOfCharacters() {
        val equalValue = evaluate(BinaryOperation(Operator.EQUALS, CharacterValue(90), CharacterValue(90)))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))


        val notEqualValue = evaluate(BinaryOperation(Operator.EQUALS, CharacterValue(90), CharacterValue(91)))
        assertThat(notEqualValue, isPureResult(equalTo(BooleanValue(false))))
    }

    @Test
    fun lessThanOperatorForCharacters() {
        assertThat(
            evaluate(BinaryOperation(Operator.LESS_THAN, CharacterValue(89), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(true)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.LESS_THAN, CharacterValue(90), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(false)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.LESS_THAN, CharacterValue(91), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(false)))
        )
    }

    @Test
    fun lessThanOrEqualOperatorForCharacters() {
        assertThat(
            evaluate(BinaryOperation(Operator.LESS_THAN_OR_EQUAL, CharacterValue(89), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(true)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.LESS_THAN_OR_EQUAL, CharacterValue(90), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(true)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.LESS_THAN_OR_EQUAL, CharacterValue(91), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(false)))
        )
    }

    @Test
    fun greaterThanOperatorForCharacters() {
        assertThat(
            evaluate(BinaryOperation(Operator.GREATER_THAN, CharacterValue(89), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(false)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.GREATER_THAN, CharacterValue(90), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(false)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.GREATER_THAN, CharacterValue(91), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(true)))
        )
    }

    @Test
    fun greaterThanOrEqualOperatorForCharacters() {
        assertThat(
            evaluate(BinaryOperation(Operator.GREATER_THAN_OR_EQUAL, CharacterValue(89), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(false)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.GREATER_THAN_OR_EQUAL, CharacterValue(90), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(true)))
        )
        assertThat(
            evaluate(BinaryOperation(Operator.GREATER_THAN_OR_EQUAL, CharacterValue(91), CharacterValue(90))),
            isPureResult(equalTo(BooleanValue(true)))
        )
    }

    @Test
    fun equalityOfSymbols() {
        val equalValue = evaluate(BinaryOperation(
            Operator.EQUALS,
            symbolValue(listOf("X"), "@a"),
            symbolValue(listOf("X"), "@a")
        ))
        assertThat(equalValue, isPureResult(equalTo(BooleanValue(true))))

        val differentName = evaluate(BinaryOperation(
            Operator.EQUALS,
            symbolValue(listOf("X"), "@a"),
            symbolValue(listOf("X"), "@b")
        ))
        assertThat(differentName, isPureResult(equalTo(BooleanValue(false))))

        val differentModule = evaluate(BinaryOperation(
            Operator.EQUALS,
            symbolValue(listOf("X"), "@a"),
            symbolValue(listOf("Y"), "@a")
        ))
        assertThat(differentModule, isPureResult(equalTo(BooleanValue(false))))
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
                ),
                namedArgumentExpressions = listOf(),
                namedArgumentValues = listOf()
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
            ),
            namedArgumentExpressions = listOf(),
            namedArgumentValues = listOf()
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
                ),
                namedArgumentExpressions = listOf(),
                namedArgumentValues = listOf()
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
            ),
            namedArgumentExpressions = listOf(),
            namedArgumentValues = listOf()
        ))))
    }

    @Test
    fun namedArgumentsAreEvaluatedInOrder() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "y" to IntegerValue(2),
                "z" to IntegerValue(3)
            ))
        )
        val expression = evaluate(
            Call(
                PrintValue,
                positionalArgumentExpressions = listOf(),
                positionalArgumentValues = listOf(),
                namedArgumentExpressions = listOf(
                    Identifier("b") to VariableReference("y"),
                    Identifier("c") to VariableReference("z")
                ),
                namedArgumentValues = listOf(
                    Identifier("a") to IntegerValue(1)
                )
            ),
            context
        )
        assertThat(expression, isPureResult(equalTo(Call(
            PrintValue,
            positionalArgumentExpressions = listOf(),
            positionalArgumentValues = listOf(),
            namedArgumentExpressions = listOf(
                Identifier("b") to IntegerValue(2),
                Identifier("c") to VariableReference("z")
            ),
            namedArgumentValues = listOf(
                Identifier("a") to IntegerValue(1)
            )
        ))))
    }

    @Test
    fun namedArgumentsAreMovedToValuesOnceFullyEvaluated() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "y" to IntegerValue(2),
                "z" to IntegerValue(3)
            ))
        )
        val expression = evaluate(
            Call(
                PrintValue,
                positionalArgumentExpressions = listOf(),
                positionalArgumentValues = listOf(),
                namedArgumentExpressions = listOf(
                    Identifier("b") to IntegerValue(2),
                    Identifier("c") to VariableReference("z")
                ),
                namedArgumentValues = listOf(
                    Identifier("a") to IntegerValue(1)
                )
            ),
            context
        )
        assertThat(expression, isPureResult(equalTo(Call(
            PrintValue,
            positionalArgumentExpressions = listOf(),
            positionalArgumentValues = listOf(),
            namedArgumentExpressions = listOf(
                Identifier("c") to VariableReference("z")
            ),
            namedArgumentValues = listOf(
                Identifier("a") to IntegerValue(1),
                Identifier("b") to IntegerValue(2)
            )
        ))))
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
    fun callingListConstructorCreatesList() {
        val context = createContext()
        val result = evaluate(
            call(
                ListConstructorValue,
                positionalArgumentValues = listOf(IntegerValue(42))
            ),
            context
        )
        assertThat(result, isPureResult(equalTo(ListValue(listOf(IntegerValue(42))))))
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
    fun whenReceiverIsPartialCallFunctionValueThenCallIsEvaluatedToPartialCall() {
        val context = createContext()
        val result = evaluate(
            call(
                PartialCallFunctionValue,
                positionalArgumentValues = listOf(PrintValue, IntegerValue(1)),
                namedArgumentValues = listOf(Identifier("x") to IntegerValue(2))
            ),
            context
        )
        assertThat(result, isPureResult(equalTo(PartialCallValue(
            receiver = PrintValue,
            positionalArguments = listOf(IntegerValue(1)),
            namedArguments = listOf(Identifier("x") to IntegerValue(2))
        ))))
    }

    @Test
    fun whenReceiverIsPartialCallValueThenCallIsEvaluatedWithCombinedArguments() {
        val context = createContext()
        val result = evaluate(
            call(
                PartialCallValue(
                    receiver = PrintValue,
                    positionalArguments = listOf(IntegerValue(1)),
                    namedArguments = listOf(Identifier("x") to IntegerValue(2))
                ),
                positionalArgumentValues = listOf(IntegerValue(3)),
                namedArgumentValues = listOf(Identifier("y") to IntegerValue(4))
            ),
            context
        )
        assertThat(result, isPureResult(equalTo(Call(
            receiver = PrintValue,
            positionalArgumentExpressions = listOf(),
            positionalArgumentValues = listOf(IntegerValue(1), IntegerValue(3)),
            namedArgumentExpressions = listOf(),
            namedArgumentValues = listOf(
                Identifier("x") to IntegerValue(2),
                Identifier("y") to IntegerValue(4)
            )
        ))))
    }

    @Test
    fun whenReceiverIsShapeTypeThenCallIsEvaluatedToShapeValue() {
        val context = createContext()
        val shapeType = ShapeTypeValue(
            constantFields = mapOf(
                Identifier("x") to IntegerValue(1)
            )
        )
        val expression = evaluate(
            call(
                receiver = shapeType,
                namedArgumentValues = listOf(
                    Identifier("y") to IntegerValue(2)
                )
            ),
            context
        )
        assertThat(expression, isPureResult(equalTo(ShapeValue(
            fields = mapOf(
                Identifier("x") to IntegerValue(1),
                Identifier("y") to IntegerValue(2)
            )
        ))))
    }

    @Test
    fun whenReceiverIsFunctionThenCallIsEvaluatedToPartiallyEvaluatedFunction() {
        val context = createContext()
        val function = FunctionValue(
            positionalParameterNames = listOf(),
            body = listOf(
                ExpressionStatement(IntegerValue(1), isReturn = false)
            ),
            scope = scopeOf(mapOf())
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
        val context = createContext()
        val function = FunctionValue(
            positionalParameterNames = listOf("arg0", "arg1"),
            body = listOf(),
            scope = Scope(frames = listOf(
                ScopeFrameMap(variables = mapOf("x" to IntegerValue(42)))
            ))
        )
        val expression = evaluate(
            call(
                receiver = function,
                positionalArgumentValues = listOf(StringValue("zero"), StringValue("one")),
                namedArgumentValues = listOf(
                    Identifier("arg2") to StringValue("two"),
                    Identifier("arg3") to StringValue("three")
                )
            ),
            context
        )
        assertThat(expression, isPureResult(equalTo(Block(
            body = listOf(),
            scope = Scope(listOf(
                ScopeFrameMap(mapOf(
                    "arg0" to StringValue("zero"),
                    "arg1" to StringValue("one"),
                    "arg2" to StringValue("two"),
                    "arg3" to StringValue("three")
                )),
                ScopeFrameMap(variables = mapOf("x" to IntegerValue(42)))
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

    @Test
    fun whenReceiverIsShapeValueThenFieldAccessIsEvaluatedToShapeFieldValue() {
        val context = createContext()
        val module = ShapeValue(
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
        val scope = Scope(frames = listOf(ScopeFrame.EMPTY))
        val expression = evaluate(ifExpression, context = createContext(scope = scope))
        assertThat(expression, isPureResult(isBlock(
            body = equalTo(listOf(returns(IntegerValue(3)))),
            scope = equalTo(scope.enter())
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
        val scope = Scope(frames = listOf(ScopeFrame.EMPTY))
        val expression = evaluate(ifExpression, createContext(scope = scope))
        assertThat(expression, isPureResult(isBlock(
            body = equalTo(listOf(returns(IntegerValue(1)))),
            scope = equalTo(scope.enter())
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

class EvaluateWhenTests {
    @Test
    fun whenExpressionIsNotValueThenExpressionIsEvaluated() {
        val whenExpression = When(
            expression = VariableReference("x"),
            expressionType = AnyType,
            branches = listOf()
        )
        val context = createContext(scope = scopeOf(mapOf("x" to BooleanValue(true))))
        val expression = evaluate(whenExpression, context)
        assertThat(expression, isPureResult(equalTo(When(
            expression = BooleanValue(true),
            expressionType = AnyType,
            branches = listOf()
        ))))
    }

    @Test
    fun whenExpressionMatchesTypeThenEvaluatesToBody() {
        val shapeId = freshShapeId()
        val shapeType1 = shapeType(
            fields = listOf(
                field("tag", symbolType(listOf("M"), "@A"), shapeId = shapeId)
            )
        )
        val shapeType2 = shapeType(
            fields = listOf(
                field("tag", symbolType(listOf("M"), "@B"), shapeId = shapeId)
            )
        )
        val unionType = unionType(members = listOf(shapeType1, shapeType2))

        val whenExpression = When(
            expression = ShapeValue(fields = mapOf(
                Identifier("tag") to symbolValue(listOf("M"), "@A")
            )),
            expressionType = unionType,
            branches = listOf(
                WhenBranch(
                    type = shapeType1,
                    body = listOf(ExpressionStatement(IntegerValue(1), isReturn = true))
                ),
                WhenBranch(
                    type = shapeType2,
                    body = listOf(ExpressionStatement(IntegerValue(2), isReturn = true))
                )
            )
        )

        val scope = Scope(listOf(ScopeFrame.EMPTY))
        val expression = evaluate(whenExpression, createContext(scope = scope))
        assertThat(expression, isPureResult(isBlock(
            body = isSequence(
                cast(equalTo(ExpressionStatement(IntegerValue(1), isReturn = true)))
            ),
            scope = equalTo(scope.enter())
        )))
    }

    @Test
    fun whenExpressionDoesNotMatchTypeThenBranchIsDropped() {
        val shapeId = freshShapeId()
        val shapeType1 = shapeType(
            fields = listOf(
                field("tag", symbolType(listOf("M"), "@A"), shapeId = shapeId)
            )
        )
        val shapeType2 = shapeType(
            fields = listOf(
                field("tag", symbolType(listOf("M"), "@B"), shapeId = shapeId)
            )
        )
        val unionType = unionType(members = listOf(shapeType1, shapeType2))

        val whenExpression = When(
            expression = ShapeValue(fields = mapOf(
                Identifier("tag") to symbolValue(listOf("M"), "@B")
            )),
            expressionType = unionType,
            branches = listOf(
                WhenBranch(
                    type = shapeType1,
                    body = listOf(ExpressionStatement(IntegerValue(1), isReturn = true))
                ),
                WhenBranch(
                    type = shapeType2,
                    body = listOf(ExpressionStatement(IntegerValue(2), isReturn = true))
                )
            )
        )

        val expression = evaluate(whenExpression)
        assertThat(expression, isPureResult(equalTo(When(
            expression = ShapeValue(fields = mapOf(
                Identifier("tag") to symbolValue(listOf("M"), "@B")
            )),
            expressionType = unionType,
            branches = listOf(
                WhenBranch(
                    type = shapeType2,
                    body = listOf(ExpressionStatement(IntegerValue(2), isReturn = true))
                )
            )
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

    @Test
    fun whenBlockHasValStatementWithValueThenStatementIsDroppedAndScopeIsUpdated() {
        val expression = evaluate(
            Block(
                body = listOf(
                    Val(Identifier("x"), IntegerValue(47)),
                    ExpressionStatement(expression = VariableReference("y"), isReturn = false)
                ),
                scope = Scope(listOf(
                    ScopeFrameMap(mapOf(
                        "y" to IntegerValue(4)
                    )),
                    ScopeFrameMap(mapOf(
                        "x" to IntegerValue(100)
                    ))
                ))
            )
        )
        assertThat(expression, isPureResult(equalTo(Block(
            body = listOf(
                ExpressionStatement(expression = VariableReference("y"), isReturn = false)
            ),
            scope = Scope(listOf(
                ScopeFrameMap(mapOf(
                    "x" to IntegerValue(47),
                    "y" to IntegerValue(4)
                )),
                ScopeFrameMap(mapOf(
                    "x" to IntegerValue(100)
                ))
            ))
        ))))
    }
}

class EvaluateValTests {
    @Test
    fun expressionIsEvaluated() {
        val context = createContext(
            scope = scopeOf(mapOf(
                "y" to IntegerValue(42)
            ))
        )
        val statement = evaluate(
            Val(Identifier("x"), VariableReference("y")),
            context
        )
        assertThat(statement, isPureResult(equalTo(Val(Identifier("x"), IntegerValue(42)))))
    }
}

private fun evaluate(
    expression: IncompleteExpression,
    context: InterpreterContext = createContext()
): EvaluationResult<Expression> {
    return expression.evaluate(context)
}

private fun evaluate(
    statement: Statement,
    context: InterpreterContext = createContext()
): EvaluationResult<Statement> {
    return statement.execute(context)
}
