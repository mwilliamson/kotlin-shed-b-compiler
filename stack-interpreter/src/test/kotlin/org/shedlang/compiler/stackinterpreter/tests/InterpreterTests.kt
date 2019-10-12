package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import kotlinx.collections.immutable.PersistentList
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Block
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap

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

    private fun executeInstructions(instructions: PersistentList<Instruction>): InterpreterValue {
        var state = state()

        while (state.instructionIndex < instructions.size) {
            val instruction = instructions[state.instructionIndex]
            state = instruction.run(state)
        }

        return state.pop().value
    }

    private fun state() = initialState()

    private fun isBool(value: Boolean): Matcher<InterpreterValue> {
        return cast(has(InterpreterBool::value, equalTo(value)))
    }

    private fun isInt(value: Int): Matcher<InterpreterValue> {
        return cast(has(InterpreterInt::value, equalTo(value.toBigInteger())))
    }
}
