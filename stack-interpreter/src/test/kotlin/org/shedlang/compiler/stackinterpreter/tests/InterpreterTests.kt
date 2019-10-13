package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import org.shedlang.compiler.EMPTY_TYPES
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.types.ModuleType

class InterpreterTests {
    @Test
    fun booleanLiteralIsEvaluatedToBoolean() {
        val node = literalBool(true)

        val value = evaluateExpression(node)

        assertThat(value, isBool(true))
    }

    @Test
    fun integerLiteralIsEvaluatedToInteger() {
        val node = literalInt(42)

        val value = evaluateExpression(node)

        assertThat(value, isInt(42))
    }

    @Test
    fun stringLiteralIsEvaluatedToString() {
        val node = literalString("hello")

        val value = evaluateExpression(node)

        assertThat(value, isString("hello"))
    }

    @Test
    fun additionAddsOperandsTogether() {
        val node = binaryOperation(BinaryOperator.ADD, literalInt(1), literalInt(2))

        val value = evaluateExpression(node)

        assertThat(value, isInt(3))
    }

    @Test
    fun subtractSubtractsOperandsFromEachOther() {
        val node = binaryOperation(BinaryOperator.SUBTRACT, literalInt(1), literalInt(2))

        val value = evaluateExpression(node)

        assertThat(value, isInt(-1))
    }

    @Test
    fun multiplyMultipliesOperands() {
        val node = binaryOperation(BinaryOperator.MULTIPLY, literalInt(3), literalInt(4))

        val value = evaluateExpression(node)

        assertThat(value, isInt(12))
    }

    @Test
    fun whenOperandsAreEqualThenIntegerEqualityEvaluatesToTrue() {
        val node = binaryOperation(BinaryOperator.EQUALS, literalInt(1), literalInt(1))

        val value = evaluateExpression(node)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenOperandsAreNotEqualThenIntegerEqualityEvaluatesToFalse() {
        val node = binaryOperation(BinaryOperator.EQUALS, literalInt(1), literalInt(2))

        val value = evaluateExpression(node)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenConditionOfIfIsTrueThenFinalValueIsResultOfTrueBranch() {
        val node = ifExpression(
            literalBool(true),
            listOf(expressionStatementReturn(literalInt(1))),
            listOf(expressionStatementReturn(literalInt(2)))
        )

        val value = evaluateExpression(node)

        assertThat(value, isInt(1))
    }

    @Test
    fun firstTrueBranchIsEvaluated() {
        val node = ifExpression(
            listOf(
                conditionalBranch(
                    literalBool(false),
                    listOf(expressionStatementReturn(literalInt(1)))
                ),
                conditionalBranch(
                    literalBool(true),
                    listOf(expressionStatementReturn(literalInt(2)))
                ),
                conditionalBranch(
                    literalBool(true),
                    listOf(expressionStatementReturn(literalInt(3)))
                ),
                conditionalBranch(
                    literalBool(false),
                    listOf(expressionStatementReturn(literalInt(4)))
                )
            ),
            listOf(expressionStatementReturn(literalInt(5)))
        )

        val value = evaluateExpression(node)

        assertThat(value, isInt(2))
    }

    @Test
    fun whenConditionOfIfIsFalseThenFinalValueIsResultOfFalseBranch() {
        val node = ifExpression(
            literalBool(false),
            listOf(expressionStatementReturn(literalInt(1))),
            listOf(expressionStatementReturn(literalInt(2)))
        )

        val value = evaluateExpression(node)

        assertThat(value, isInt(2))
    }

    @Test
    fun variableIntroducedByValCanBeRead() {
        val target = targetVariable("x")
        val reference = variableReference("x")
        val block = block(
            listOf(
                valStatement(target = target, expression = literalInt(42)),
                expressionStatementReturn(reference)
            )
        )

        val references = ResolvedReferencesMap(mapOf(
            reference.nodeId to target
        ))

        val value = evaluateBlock(block, references = references)

        assertThat(value, isInt(42))
    }

    @Test
    fun canCallFunctionDefinedInModuleWithZeroArguments() {
        val function = function(
            name = "main",
            body = listOf(
                expressionStatementReturn(
                    literalInt(42)
                )
            )
        )
        val moduleName = listOf(Identifier("Example"))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                body = listOf(function)
            )
        )

        val image = loadModuleSet(ModuleSet(listOf(module)))
        val value = executeInstructions(
            persistentListOf(
                InitModule(moduleName),
                LoadGlobal(function.nodeId),
                Call(argumentCount = 0)
            ),
            image = image
        )

        assertThat(value, isInt(42))
    }

    @Test
    fun canCallFunctionDefinedInModuleWithPositionalArguments() {
        val firstParameter = parameter("first")
        val secondParameter = parameter("second")
        val firstReference = variableReference("first")
        val secondReference = variableReference("second")
        val function = function(
            name = "subtract",
            parameters = listOf(
                firstParameter,
                secondParameter
            ),
            body = listOf(
                expressionStatementReturn(
                    binaryOperation(
                        BinaryOperator.SUBTRACT,
                        firstReference,
                        secondReference
                    )
                )
            )
        )
        val moduleName = listOf(Identifier("Example"))
        val references = ResolvedReferencesMap(mapOf(
            firstReference.nodeId to firstParameter,
            secondReference.nodeId to secondParameter
        ))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                body = listOf(function)
            ),
            references = references
        )

        val image = loadModuleSet(ModuleSet(listOf(module)))
        val value = executeInstructions(
            persistentListOf(
                InitModule(moduleName),
                LoadGlobal(function.nodeId),
                PushValue(InterpreterInt(1.toBigInteger())),
                PushValue(InterpreterInt(2.toBigInteger())),
                Call(argumentCount = 2)
            ),
            image = image
        )

        assertThat(value, isInt(-1))
    }

    @Test
    fun functionDeclarationCreatesFunctionValueThatCanBeCalledWithZeroArguments() {
        val function = function(
            name = "main",
            body = listOf(
                expressionStatementReturn(
                    literalInt(42)
                )
            )
        )
        val functionReference = variableReference("main")
        val call = call(
            receiver = functionReference
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function
        ))

        val loader = loader(references = references)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions)

        assertThat(value, isInt(42))
    }

    @Test
    fun functionDeclarationCreatesFunctionValueThatCanBeCalledWithPositionalArguments() {
        val firstParameter = parameter("first")
        val secondParameter = parameter("second")
        val firstReference = variableReference("first")
        val secondReference = variableReference("second")
        val function = function(
            name = "subtract",
            parameters = listOf(
                firstParameter,
                secondParameter
            ),
            body = listOf(
                expressionStatementReturn(
                    binaryOperation(
                        BinaryOperator.SUBTRACT,
                        firstReference,
                        secondReference
                    )
                )
            )
        )
        val functionReference = variableReference("main")
        val call = call(
            receiver = functionReference,
            positionalArguments = listOf(
                literalInt(1),
                literalInt(2)
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            firstReference.nodeId to firstParameter,
            secondReference.nodeId to secondParameter
        ))

        val loader = loader(references = references)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions)

        assertThat(value, isInt(-1))
    }

    private fun evaluateBlock(block: Block, references: ResolvedReferences): InterpreterValue {
        val instructions = loader(references = references).loadBlock(block)
        return executeInstructions(instructions)
    }

    private fun evaluateExpression(node: ExpressionNode): InterpreterValue {
        val instructions = loader().loadExpression(node)
        return executeInstructions(instructions)
    }

    private fun executeInstructions(instructions: PersistentList<Instruction>, image: Image = Image.EMPTY): InterpreterValue {
        val finalState = org.shedlang.compiler.stackinterpreter.executeInstructions(instructions, image = image)
        return finalState.popTemporary().second
    }

    private fun loader(references: ResolvedReferences = ResolvedReferencesMap.EMPTY): Loader {
        return Loader(references = references)
    }

    private fun isBool(value: Boolean): Matcher<InterpreterValue> {
        return cast(has(InterpreterBool::value, equalTo(value)))
    }

    private fun isInt(value: Int): Matcher<InterpreterValue> {
        return cast(has(InterpreterInt::value, equalTo(value.toBigInteger())))
    }

    private fun isString(value: String): Matcher<InterpreterValue> {
        return cast(has(InterpreterString::value, equalTo(value)))
    }

    private fun stubbedModule(
        name: List<Identifier>,
        node: ModuleNode,
        references: ResolvedReferences = ResolvedReferencesMap.EMPTY
    ): Module.Shed {
        return Module.Shed(
            name = name,
            type = ModuleType(mapOf()),
            types = EMPTY_TYPES,
            references = references,
            node = node
        )
    }
}
