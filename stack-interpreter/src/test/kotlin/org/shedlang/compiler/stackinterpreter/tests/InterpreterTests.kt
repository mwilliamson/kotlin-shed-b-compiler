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
    fun canExecuteFunctionInModule() {
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

        val image = loadModule(module)
        val value = executeInstructions(
            persistentListOf(
                InitModule(moduleName),
                LoadGlobal(function.nodeId),
                Call()
            ),
            image = image
        )

        assertThat(value, isInt(42))
    }

    private fun evaluateBlock(block: Block, references: ResolvedReferences): InterpreterValue {
        val instructions = loader(references = references).loadBlock(block)
        return executeInstructions(instructions)
    }

    private fun evaluateExpression(node: ExpressionNode): InterpreterValue {
        val instructions = loader().loadExpression(node)
        return executeInstructions(instructions)
    }

    private fun loader(references: ResolvedReferences = ResolvedReferencesMap.EMPTY): Loader {
        return Loader(references = references)
    }

    private fun executeInstructions(instructions: PersistentList<Instruction>, image: Image = Image.EMPTY): InterpreterValue {
        var state = state(image = image, instructions = instructions)

        while (true) {
            val instruction = state.instruction()
            if (instruction == null) {
                return state.popTemporary().component2()
            } else {
                state = instruction.run(state)
            }
        }
    }

    private fun state(image: Image, instructions: PersistentList<Instruction>): InterpreterState {
        return initialState(image = image, instructions = instructions)
    }

    private fun isBool(value: Boolean): Matcher<InterpreterValue> {
        return cast(has(InterpreterBool::value, equalTo(value)))
    }

    private fun isInt(value: Int): Matcher<InterpreterValue> {
        return cast(has(InterpreterInt::value, equalTo(value.toBigInteger())))
    }

    private fun stubbedModule(name: List<Identifier>, node: ModuleNode): Module.Shed {
        return Module.Shed(
            name = name,
            type = ModuleType(mapOf()),
            types = EMPTY_TYPES,
            references = ResolvedReferencesMap(mapOf()),
            node = node
        )
    }
}
