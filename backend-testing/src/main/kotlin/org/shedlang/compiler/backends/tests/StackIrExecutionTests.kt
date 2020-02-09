package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.Types
import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.UnaryOperator
import org.shedlang.compiler.backends.SimpleCodeInspector
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.types.*

interface StackIrExecutionEnvironment {
    fun executeInstructions(instructions: List<Instruction>, type: Type): IrValue
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

    @Test
    fun whenOperandsAreEqualThenBooleanInequalityEvaluatesToFalse() {
        val left = literalBool(true)
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalBool(true))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to BoolType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenOperandsAreNotEqualThenBooleanInequalityEvaluatesToTrue() {
        val left = literalBool(true)
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalBool(false))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to BoolType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenOperandsAreBothTrueThenBooleanAndEvaluatesToTrue() {
        assertBooleanAnd(false, false, false)
        assertBooleanAnd(false, true, false)
        assertBooleanAnd(true, false, false)
        assertBooleanAnd(true, true, true)
    }

    private fun assertBooleanAnd(leftValue: Boolean, rightValue: Boolean, expectedValue: Boolean) {
        val left = literalBool(leftValue)
        val node = binaryOperation(BinaryOperator.AND, left, literalBool(rightValue))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to BoolType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(expectedValue))
    }

    @Test
    fun whenEitherOperandIsTrueThenBooleanOrEvaluatesToTrue() {
        assertBooleanOr(false, false, false)
        assertBooleanOr(false, true, true)
        assertBooleanOr(true, false, true)
        assertBooleanOr(true, true, true)
    }

    private fun assertBooleanOr(leftValue: Boolean, rightValue: Boolean, expectedValue: Boolean) {
        val left = literalBool(leftValue)
        val node = binaryOperation(BinaryOperator.OR, left, literalBool(rightValue))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to BoolType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(expectedValue))
    }

    @Test
    fun codePointOperandsAreEqualIfAndOnlyIfCodePointEqualityEvaluatesToTrue() {
        assertCodePointBinaryOperation(BinaryOperator.EQUALS, 'X', 'X', isBool(true))
        assertCodePointBinaryOperation(BinaryOperator.EQUALS, 'X', 'Y', isBool(false))
    }

    @Test
    fun codePointOperandsAreNotEqualIfAndOnlyIfCodePointEqualityEvaluatesToFalse() {
        assertCodePointBinaryOperation(BinaryOperator.NOT_EQUAL, 'X', 'X', isBool(false))
        assertCodePointBinaryOperation(BinaryOperator.NOT_EQUAL, 'X', 'Y', isBool(true))
    }

    @Test
    fun leftCodePointOperandIsLessThanRightOperandIfAndOnlyIfCodePointLessThanOperatorEvaluatesToFalse() {
        assertCodePointBinaryOperation(BinaryOperator.LESS_THAN, 'X', 'Y', isBool(true))
        assertCodePointBinaryOperation(BinaryOperator.LESS_THAN, 'Y', 'Y', isBool(false))
        assertCodePointBinaryOperation(BinaryOperator.LESS_THAN, 'Z', 'Y', isBool(false))
    }

    @Test
    fun leftCodePointOperandIsLessThanOrEqualRightOperandIfAndOnlyIfCodePointLessThanOrEqualOperatorEvaluatesToFalse() {
        assertCodePointBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 'X', 'Y', isBool(true))
        assertCodePointBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 'Y', 'Y', isBool(true))
        assertCodePointBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 'Z', 'Y', isBool(false))
    }

    @Test
    fun leftCodePointOperandIsGreaterThanRightOperandIfAndOnlyIfCodePointGreaterThanOperatorEvaluatesToFalse() {
        assertCodePointBinaryOperation(BinaryOperator.GREATER_THAN, 'X', 'Y', isBool(false))
        assertCodePointBinaryOperation(BinaryOperator.GREATER_THAN, 'Y', 'Y', isBool(false))
        assertCodePointBinaryOperation(BinaryOperator.GREATER_THAN, 'Z', 'Y', isBool(true))
    }

    @Test
    fun leftCodePointOperandIsGreaterThanOrEqualRightOperandIfAndOnlyIfCodePointGreaterThanOrEqualOperatorEvaluatesToFalse() {
        assertCodePointBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 'X', 'Y', isBool(false))
        assertCodePointBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 'Y', 'Y', isBool(true))
        assertCodePointBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 'Z', 'Y', isBool(true))
    }

    private fun assertCodePointBinaryOperation(operator: BinaryOperator, left: Char, right: Char, expected: Matcher<IrValue>) {
        val left = literalCodePoint(left)
        val node = binaryOperation(operator, left, literalCodePoint(right))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to CodePointType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, expected)
    }

    @Test
    fun integerAdditionAddsOperandsTogether() {
        val left = literalInt(1)
        val node = binaryOperation(BinaryOperator.ADD, left, literalInt(2))
        val types = createTypes(expressionTypes = mapOf(left.nodeId to IntType))

        val value = evaluateExpression(node, type = IntType, types = types)

        assertThat(value, isInt(3))
    }

    @Test
    fun subtractSubtractsOperandsFromEachOther() {
        val node = binaryOperation(BinaryOperator.SUBTRACT, literalInt(1), literalInt(2))

        val value = evaluateExpression(node, type = IntType)

        assertThat(value, isInt(-1))
    }

    @Test
    fun multiplyMultipliesOperands() {
        val node = binaryOperation(BinaryOperator.MULTIPLY, literalInt(3), literalInt(4))

        val value = evaluateExpression(node, type = IntType)

        assertThat(value, isInt(12))
    }

    @Test
    fun whenOperandsAreEqualThenIntegerEqualityEvaluatesToTrue() {
        val left = literalInt(1)
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalInt(1))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to IntType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenOperandsAreNotEqualThenIntegerEqualityEvaluatesToFalse() {
        val left = literalInt(1)
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalInt(2))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to IntType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenOperandsAreEqualThenIntegerInequalityEvaluatesToFalse() {
        val left = literalInt(1)
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalInt(1))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to IntType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenOperandsAreNotEqualThenIntegerInequalityEvaluatesToTrue() {
        val left = literalInt(1)
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalInt(2))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to IntType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun stringAdditionConcatenatesStrings() {
        val left = literalString("hello ")
        val node = binaryOperation(BinaryOperator.ADD, left, literalString("world"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = StringType, types = types)

        assertThat(value, isString("hello world"))
    }

    @Test
    fun whenOperandsAreEqualThenStringEqualityReturnsTrue() {
        val left = literalString("hello")
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalString("hello"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenOperandsHaveSameLengthButDifferentBytesThenStringEqualityReturnsFalse() {
        val left = literalString("hello")
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalString("world"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenLeftStringIsPrefixOfRightStringThenStringEqualityReturnsFalse() {
        val left = literalString("hello")
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalString("helloo"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenRightStringIsPrefixOfLeftStringThenStringEqualityReturnsFalse() {
        val left = literalString("helloo")
        val node = binaryOperation(BinaryOperator.EQUALS, left, literalString("hello"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenOperandsAreEqualThenStringInequalityReturnsFalse() {
        val left = literalString("hello")
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalString("hello"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenLeftStringIsPrefixOfRightStringThenStringInequalityReturnsTrue() {
        val left = literalString("hello")
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalString("helloo"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenRightStringIsPrefixOfLeftStringThenStringInequalityReturnsTrue() {
        val left = literalString("helloo")
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalString("hello"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenOperandsHaveSameLengthButDifferentBytesThenStringInequalityReturnsTrue() {
        val left = literalString("hello")
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, left, literalString("world"))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to StringType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenDiscriminatorMatchesThenIsOperationIsTrue() {
        val shapeDeclaration = shape("X")
        val shapeReference = variableReference("X")

        val receiverTarget = targetVariable("receiver")
        val receiverDeclaration = valStatement(
            target = receiverTarget,
            expression = call(shapeReference)
        )
        val receiverReference = variableReference("receiver")
        val node = isOperation(receiverReference, shapeReference)

        val tag = tag(listOf("Example"), "Tag")
        val inspector = SimpleCodeInspector(
            discriminatorsForIsExpressions = mapOf(
                node to discriminator(tagValue(tag, "X"))
            ),
            shapeFields = mapOf(
                shapeDeclaration to listOf()
            ),
            shapeTagValues = mapOf(
                shapeDeclaration to tagValue(tag, "X")
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            receiverReference.nodeId to receiverTarget
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                shapeReference.nodeId to MetaType(shapeType())
            )
        )

        val loader = loader(inspector = inspector, references = references, types = types)
        val instructions = loader.loadModuleStatement(shapeDeclaration)
            .addAll(loader.loadFunctionStatement(receiverDeclaration))
            .addAll(loader.loadExpression(node))
        val value = executeInstructions(instructions, type = BoolType)

        assertThat(value, isBool(true))
    }

    @Test
    fun whenDiscriminatorDoesNotMatchThenIsOperationIsFalse() {
        val shapeDeclaration = shape("X")
        val shapeReference = variableReference("X")

        val receiverTarget = targetVariable("receiver")
        val receiverDeclaration = valStatement(
            target = receiverTarget,
            expression = call(shapeReference)
        )
        val receiverReference = variableReference("receiver")
        val node = isOperation(receiverReference, shapeReference)

        val tag = tag(listOf("Example"), "Tag")
        val inspector = SimpleCodeInspector(
            discriminatorsForIsExpressions = mapOf(
                node to discriminator(tagValue(tag, "C"))
            ),
            shapeFields = mapOf(
                shapeDeclaration to listOf()
            ),
            shapeTagValues = mapOf(
                shapeDeclaration to tagValue(tag, "B")
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            receiverReference.nodeId to receiverTarget
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                shapeReference.nodeId to MetaType(shapeType())
            )
        )

        val loader = loader(inspector = inspector, references = references, types = types)
        val instructions = loader.loadModuleStatement(shapeDeclaration)
            .addAll(loader.loadFunctionStatement(receiverDeclaration))
            .addAll(loader.loadExpression(node))
        val value = executeInstructions(instructions, type = BoolType)

        assertThat(value, isBool(false))
    }

    @Test
    fun whenConditionOfIfIsTrueThenFinalValueIsResultOfTrueBranch() {
        val node = ifExpression(
            literalBool(true),
            listOf(expressionStatementReturn(literalInt(1))),
            listOf(expressionStatementReturn(literalInt(2)))
        )

        val value = evaluateExpression(node, type = IntType)

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

        val value = evaluateExpression(node, type = IntType)

        assertThat(value, isInt(2))
    }

    @Test
    fun whenConditionOfIfIsFalseThenFinalValueIsResultOfFalseBranch() {
        val node = ifExpression(
            literalBool(false),
            listOf(expressionStatementReturn(literalInt(1))),
            listOf(expressionStatementReturn(literalInt(2)))
        )

        val value = evaluateExpression(node, type = IntType)

        assertThat(value, isInt(2))
    }

    private fun evaluateExpression(node: ExpressionNode, type: Type, types: Types = createTypes()): IrValue {
        val instructions = loader(types = types).loadExpression(node)
        return executeInstructions(instructions, type = type)
    }

    private fun executeInstructions(instructions: List<Instruction>, type: Type): IrValue {
        return environment.executeInstructions(instructions, type = type)
    }

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
