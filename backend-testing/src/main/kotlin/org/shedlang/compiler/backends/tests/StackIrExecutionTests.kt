package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.SimpleCodeInspector
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.types.*

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
    fun unicodeScalarLiteralIsEvaluatedToUnicodeScalar() {
        val node = literalUnicodeScalar('X')

        val value = evaluateExpression(node, type = UnicodeScalarType)

        assertThat(value, isUnicodeScalar('X'.toInt()))
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
    fun unicodeScalarOperandsAreEqualIfAndOnlyIfUnicodeScalarEqualityEvaluatesToTrue() {
        assertUnicodeScalarBinaryOperation(BinaryOperator.EQUALS, 'X', 'X', isBool(true))
        assertUnicodeScalarBinaryOperation(BinaryOperator.EQUALS, 'X', 'Y', isBool(false))
    }

    @Test
    fun unicodeScalarOperandsAreNotEqualIfAndOnlyIfUnicodeScalarEqualityEvaluatesToFalse() {
        assertUnicodeScalarBinaryOperation(BinaryOperator.NOT_EQUAL, 'X', 'X', isBool(false))
        assertUnicodeScalarBinaryOperation(BinaryOperator.NOT_EQUAL, 'X', 'Y', isBool(true))
    }

    @Test
    fun leftUnicodeScalarOperandIsLessThanRightOperandIfAndOnlyIfUnicodeScalarLessThanOperatorEvaluatesToFalse() {
        assertUnicodeScalarBinaryOperation(BinaryOperator.LESS_THAN, 'X', 'Y', isBool(true))
        assertUnicodeScalarBinaryOperation(BinaryOperator.LESS_THAN, 'Y', 'Y', isBool(false))
        assertUnicodeScalarBinaryOperation(BinaryOperator.LESS_THAN, 'Z', 'Y', isBool(false))
    }

    @Test
    fun leftUnicodeScalarOperandIsLessThanOrEqualRightOperandIfAndOnlyIfUnicodeScalarLessThanOrEqualOperatorEvaluatesToFalse() {
        assertUnicodeScalarBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 'X', 'Y', isBool(true))
        assertUnicodeScalarBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 'Y', 'Y', isBool(true))
        assertUnicodeScalarBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 'Z', 'Y', isBool(false))
    }

    @Test
    fun leftUnicodeScalarOperandIsGreaterThanRightOperandIfAndOnlyIfUnicodeScalarGreaterThanOperatorEvaluatesToFalse() {
        assertUnicodeScalarBinaryOperation(BinaryOperator.GREATER_THAN, 'X', 'Y', isBool(false))
        assertUnicodeScalarBinaryOperation(BinaryOperator.GREATER_THAN, 'Y', 'Y', isBool(false))
        assertUnicodeScalarBinaryOperation(BinaryOperator.GREATER_THAN, 'Z', 'Y', isBool(true))
    }

    @Test
    fun leftUnicodeScalarOperandIsGreaterThanOrEqualRightOperandIfAndOnlyIfUnicodeScalarGreaterThanOrEqualOperatorEvaluatesToFalse() {
        assertUnicodeScalarBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 'X', 'Y', isBool(false))
        assertUnicodeScalarBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 'Y', 'Y', isBool(true))
        assertUnicodeScalarBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 'Z', 'Y', isBool(true))
    }

    private fun assertUnicodeScalarBinaryOperation(operator: BinaryOperator, left: Char, right: Char, expected: Matcher<IrValue>) {
        val left = literalUnicodeScalar(left)
        val node = binaryOperation(operator, left, literalUnicodeScalar(right))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to UnicodeScalarType)
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
    fun leftIntOperandIsLessThanRightOperandIfAndOnlyIfIntLessThanOperatorEvaluatesToFalse() {
        assertIntBinaryOperation(BinaryOperator.LESS_THAN, 1, 2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN, 2, 2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN, 3, 2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN, -3, -2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN, -2, -2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN, -1, -2, isBool(false))
    }

    @Test
    fun leftIntOperandIsLessThanOrEqualRightOperandIfAndOnlyIfIntLessThanOrEqualOperatorEvaluatesToFalse() {
        assertIntBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 1, 2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 2, 2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, 3, 2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, -3, -2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, -2, -2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.LESS_THAN_OR_EQUAL, -1, -2, isBool(false))
    }

    @Test
    fun leftIntOperandIsGreaterThanRightOperandIfAndOnlyIfIntGreaterThanOperatorEvaluatesToFalse() {
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN, 1, 2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN, 2, 2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN, 3, 2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN, -3, -2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN, -2, -2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN, -1, -2, isBool(true))
    }

    @Test
    fun leftIntOperandIsGreaterThanOrEqualRightOperandIfAndOnlyIfIntGreaterThanOrEqualOperatorEvaluatesToFalse() {
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 1, 2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 2, 2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, 3, 2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, -3, -2, isBool(false))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, -2, -2, isBool(true))
        assertIntBinaryOperation(BinaryOperator.GREATER_THAN_OR_EQUAL, -1, -2, isBool(true))
    }

    private fun assertIntBinaryOperation(operator: BinaryOperator, left: Int, right: Int, expected: Matcher<IrValue>) {
        val left = literalInt(left)
        val node = binaryOperation(operator, left, literalInt(right))
        val types = createTypes(
            expressionTypes = mapOf(left.nodeId to IntType)
        )

        val value = evaluateExpression(node, type = BoolType, types = types)

        assertThat(value, expected)
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
        val tagValue = tagValue(tag, "X")
        val inspector = SimpleCodeInspector(
            discriminatorsForIsExpressions = mapOf(
                node to discriminator(tagValue)
            ),
            shapeFields = mapOf(
                shapeDeclaration to listOf()
            ),
            shapeTagValues = mapOf(
                shapeDeclaration to tagValue
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            receiverReference.nodeId to receiverTarget
        ))
        val shapeType = shapeType(tagValue = tagValue)
        val types = createTypes(
            expressionTypes = mapOf(
                shapeReference.nodeId to metaType(shapeType)
            ),
            variableTypes = mapOf(
                shapeDeclaration.nodeId to metaType(shapeType)
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
        val tagValue = tagValue(tag, "B")
        val inspector = SimpleCodeInspector(
            discriminatorsForIsExpressions = mapOf(
                node to discriminator(tagValue(tag, "C"))
            ),
            shapeFields = mapOf(
                shapeDeclaration to listOf()
            ),
            shapeTagValues = mapOf(
                shapeDeclaration to tagValue
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            receiverReference.nodeId to receiverTarget
        ))
        val shapeType = shapeType(tagValue = tagValue)
        val types = createTypes(
            expressionTypes = mapOf(
                shapeReference.nodeId to metaType(shapeType)
            ),
            variableTypes = mapOf(
                shapeDeclaration.nodeId to metaType(shapeType)
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
    fun canAccessFieldsOnShapeValue() {
        val shapeDeclaration = shape("Pair")
        val shapeReference = variableReference("Pair")

        val receiverTarget = targetVariable("receiver")
        val receiverDeclaration = valStatement(
            target = receiverTarget,
            expression = call(
                shapeReference,
                namedArguments = listOf(
                    callNamedArgument("first", literalInt(1)),
                    callNamedArgument("second", literalInt(2))
                )
            )
        )
        val receiverReference = variableReference("receiver")
        val fieldAccess = fieldAccess(receiverReference, "second")

        val inspector = SimpleCodeInspector(
            shapeFields = mapOf(
                shapeDeclaration to listOf(
                    fieldInspector(name = "first"),
                    fieldInspector(name = "second")
                )
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            receiverReference.nodeId to receiverTarget
        ))
        val shapeType = shapeType(
            fields = listOf(
                field("first", type = IntType),
                field("second", type = IntType)
            )
        )
        val types = createTypes(
            expressionTypes = mapOf(
                receiverReference.nodeId to shapeType,
                shapeReference.nodeId to metaType(shapeType)
            ),
            variableTypes = mapOf(
                shapeDeclaration.nodeId to metaType(shapeType)
            )
        )

        val loader = loader(inspector = inspector, references = references, types = types)
        val instructions = loader.loadModuleStatement(shapeDeclaration)
            .addAll(loader.loadFunctionStatement(receiverDeclaration))
            .addAll(loader.loadExpression(fieldAccess))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(2))
    }

    @Test
    fun canAccessFieldsOnShapeValueWithTagValue() {
        val shapeDeclaration = shape("Pair")
        val shapeReference = variableReference("Pair")

        val receiverTarget = targetVariable("receiver")
        val receiverDeclaration = valStatement(
            target = receiverTarget,
            expression = call(
                shapeReference,
                namedArguments = listOf(
                    callNamedArgument("first", literalInt(1)),
                    callNamedArgument("second", literalInt(2))
                )
            )
        )
        val receiverReference = variableReference("receiver")
        val fieldAccess = fieldAccess(receiverReference, "second")

        val tag = tag(listOf("Example"), "Tag")
        val tagValue = tagValue(tag, "TagValue")
        val inspector = SimpleCodeInspector(
            shapeFields = mapOf(
                shapeDeclaration to listOf(
                    fieldInspector(name = "first"),
                    fieldInspector(name = "second")
                )
            ),
            shapeTagValues = mapOf(
                shapeDeclaration to tagValue
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            receiverReference.nodeId to receiverTarget
        ))
        val shapeType = shapeType(
            fields = listOf(
                field("first", type = IntType),
                field("second", type = IntType)
            ),
            tagValue = tagValue
        )
        val types = createTypes(
            expressionTypes = mapOf(
                receiverReference.nodeId to shapeType,
                shapeReference.nodeId to metaType(shapeType)
            ),
            variableTypes = mapOf(
                shapeDeclaration.nodeId to metaType(shapeType)
            )
        )

        val loader = loader(inspector = inspector, references = references, types = types)
        val instructions = loader.loadModuleStatement(shapeDeclaration)
            .addAll(loader.loadFunctionStatement(receiverDeclaration))
            .addAll(loader.loadExpression(fieldAccess))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(2))
    }

    @Test
    fun canDestructureFieldsInVal() {
        val shapeDeclaration = shape("Pair")
        val shapeReference = variableReference("Pair")

        val firstTarget = targetVariable("target1")
        val secondTarget = targetVariable("target2")
        val fieldsTarget = targetFields(listOf(
            fieldName("first") to firstTarget,
            fieldName("second") to secondTarget
        ))
        val receiverDeclaration = valStatement(
            target = fieldsTarget,
            expression = call(
                shapeReference,
                namedArguments = listOf(
                    callNamedArgument("first", literalInt(1)),
                    callNamedArgument("second", literalInt(5))
                )
            )
        )

        val firstReference = variableReference("first")
        val secondReference = variableReference("second")
        val addition = binaryOperation(BinaryOperator.SUBTRACT, firstReference, secondReference)

        val inspector = SimpleCodeInspector(
            shapeFields = mapOf(
                shapeDeclaration to listOf(
                    fieldInspector(name = "first"),
                    fieldInspector(name = "second")
                )
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            firstReference.nodeId to firstTarget,
            secondReference.nodeId to secondTarget
        ))
        val shapeType = shapeType(
            fields = listOf(
                field("first", type = IntType),
                field("second", type = IntType)
            )
        )
        val types = createTypes(
            expressionTypes = mapOf(
                shapeReference.nodeId to metaType(shapeType),
                firstReference.nodeId to IntType
            ),
            targetTypes = mapOf(
                fieldsTarget.nodeId to shapeType
            ),
            variableTypes = mapOf(
                shapeDeclaration.nodeId to metaType(shapeType)
            )
        )

        val loader = loader(inspector = inspector, references = references, types = types)
        val instructions = loader.loadModuleStatement(shapeDeclaration)
            .addAll(loader.loadFunctionStatement(receiverDeclaration))
            .addAll(loader.loadExpression(addition))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(-4))
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

    @Test
    fun firstMatchingBranchOfWhenIsEvaluated() {
        val shape1 = unionMember("Shape1")
        val shape2 = unionMember("Shape2")
        val shape3 = unionMember("Shape3")
        val shape4 = unionMember("Shape4")
        val unionDeclaration = union(name = "U", members = listOf(shape1, shape2, shape3, shape4))
        val shape2Reference = variableReference("Shape2")

        val branch1 = whenBranch(
            body = listOf(expressionStatementReturn(literalInt(1)))
        )
        val branch2 = whenBranch(
            body = listOf(expressionStatementReturn(literalInt(2)))
        )
        val branch3 = whenBranch(
            body = listOf(expressionStatementReturn(literalInt(3)))
        )
        val branch4 = whenBranch(
            body = listOf(expressionStatementReturn(literalInt(4)))
        )
        val node = whenExpression(
            expression = call(shape2Reference),
            conditionalBranches = listOf(branch1, branch2, branch3, branch4)
        )

        val tag = tag(listOf("Example"), "Tag")
        val inspector = SimpleCodeInspector(
            discriminatorsForWhenBranches = mapOf(
                Pair(node, branch1) to discriminator(tagValue(tag, "value1")),
                Pair(node, branch2) to discriminator(tagValue(tag, "value2")),
                Pair(node, branch3) to discriminator(tagValue(tag, "value3")),
                Pair(node, branch4) to discriminator(tagValue(tag, "value4"))
            ),
            shapeFields = mapOf(
                shape1 to listOf(),
                shape2 to listOf(),
                shape3 to listOf(),
                shape4 to listOf()
            ),
            shapeTagValues = mapOf(
                shape1 to tagValue(tag, "value1"),
                shape2 to tagValue(tag, "value2"),
                shape3 to tagValue(tag, "value3"),
                shape4 to tagValue(tag, "value4")
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shape2Reference.nodeId to shape2
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                shape2Reference.nodeId to metaType(shapeType())
            ),
            variableTypes = mapOf(
                shape1.nodeId to metaType(shapeType(name = "Shape1", tagValue = tagValue(tag, "value1"))),
                shape2.nodeId to metaType(shapeType(name = "Shape2", tagValue = tagValue(tag, "value2"))),
                shape3.nodeId to metaType(shapeType(name = "Shape3", tagValue = tagValue(tag, "value3"))),
                shape4.nodeId to metaType(shapeType(name = "Shape4", tagValue = tagValue(tag, "value4")))
            )
        )
        val loader = loader(inspector = inspector, references = references, types = types)
        val instructions = loader.loadModuleStatement(unionDeclaration)
            .addAll(loader.loadExpression(node))
        val value = executeInstructions(instructions, type = IntType)

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

        val value = evaluateBlock(block, type = IntType, references = references)

        assertThat(value, isInt(42))
    }

    @Test
    fun canCreateAndDestructureTuple() {
        val firstTarget = targetVariable("target1")
        val secondTarget = targetVariable("target2")
        val valStatement = valStatement(
            target = targetTuple(listOf(firstTarget, secondTarget)),
            expression = tupleNode(listOf(literalInt(1), literalInt(5)))
        )
        val firstReference = variableReference("first")
        val secondReference = variableReference("second")
        val addition = binaryOperation(BinaryOperator.SUBTRACT, firstReference, secondReference)
        val references = ResolvedReferencesMap(mapOf(
            firstReference.nodeId to firstTarget,
            secondReference.nodeId to secondTarget
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                firstReference.nodeId to IntType
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadFunctionStatement(valStatement)
            .addAll(loader.loadExpression(addition))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(-4))
    }

    @Test
    fun canIgnoreTarget() {
        val block = block(listOf(
            valStatement(
                target = targetIgnore(),
                expression = literalInt()
            )
        ))

        val value = evaluateBlock(block, type = UnitType)

        assertThat(value, isUnit)
    }

    @Test
    fun emptyFunctionReturnsUnit() {
        val function = function(
            name = "main",
            body = listOf()
        )
        val functionReference = variableReference("main")
        val call = call(receiver = functionReference)
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                functionReference.nodeId to functionType()
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions, type = UnitType)

        assertThat(value, isUnit)
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
        val types = createTypes(
            expressionTypes = mapOf(
                functionReference.nodeId to functionType()
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions, type = IntType)

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
                literalInt(5)
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            firstReference.nodeId to firstParameter,
            secondReference.nodeId to secondParameter
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                functionReference.nodeId to functionType()
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(-4))
    }

    @Test
    fun functionDeclarationCreatesFunctionValueThatCanBeCalledWithNamedArguments() {
        val firstParameter = parameter("first")
        val secondParameter = parameter("second")
        val firstReference = variableReference("first")
        val secondReference = variableReference("second")
        val function = function(
            name = "subtract",
            namedParameters = listOf(firstParameter, secondParameter),
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
            namedArguments = listOf(
                callNamedArgument("first", literalInt(1)),
                callNamedArgument("second", literalInt(5))
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            firstReference.nodeId to firstParameter,
            secondReference.nodeId to secondParameter
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                functionReference.nodeId to functionType()
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(-4))
    }

    @Test
    fun functionDeclarationCanMixPositionalAndNamedArguments() {
        val firstParameter = parameter("first")
        val secondParameter = parameter("second")
        val firstReference = variableReference("first")
        val secondReference = variableReference("second")
        val function = function(
            name = "subtract",
            parameters = listOf(firstParameter),
            namedParameters = listOf(secondParameter),
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
                literalInt(1)
            ),
            namedArguments = listOf(
                callNamedArgument("second", literalInt(5))
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            firstReference.nodeId to firstParameter,
            secondReference.nodeId to secondParameter
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                functionReference.nodeId to functionType()
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(-4))
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
        val functionType = functionType(returns = IntType)
        val moduleName = listOf(Identifier("Example"))
        val functionReference = export("main")
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function
        ))
        val moduleType = moduleType(fields = mapOf(function.name.value to functionType))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                exports = listOf(functionReference),
                body = listOf(function)
            ),
            type = moduleType,
            references = references
        )

        val moduleSet = ModuleSet(listOf(module))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(moduleName),
                ModuleLoad(moduleName),
                FieldAccess(Identifier("main"), receiverType = moduleType),
                Call(positionalArgumentCount = 0, namedArgumentNames = listOf())
            ),
            moduleSet = moduleSet,
            type = IntType
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
        val functionType = functionType(
            positionalParameters = listOf(IntType, IntType),
            returns = IntType
        )
        val moduleName = listOf(Identifier("Example"))
        val functionReference = export("subtract")
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            firstReference.nodeId to firstParameter,
            secondReference.nodeId to secondParameter
        ))
        val moduleType = moduleType(fields = mapOf(function.name.value to functionType))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                exports = listOf(functionReference),
                body = listOf(function)
            ),
            type = moduleType,
            references = references
        )

        val moduleSet = ModuleSet(listOf(module))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(moduleName),
                ModuleLoad(moduleName),
                FieldAccess(Identifier("subtract"), receiverType = moduleType),
                PushValue(IrInt(1)),
                PushValue(IrInt(5)),
                Call(positionalArgumentCount = 2, namedArgumentNames = listOf())
            ),
            type = IntType,
            moduleSet = moduleSet
        )

        assertThat(value, isInt(-4))
    }

    @Test
    fun functionCanReferenceVariablesFromOuterScope() {
        val valueTarget = targetVariable("value")
        val valueDeclaration = valStatement(valueTarget, literalInt(42))
        val valueReference = variableReference("value")
        val function = function(
            name = "main",
            body = listOf(
                expressionStatementReturn(valueReference)
            )
        )
        val functionType = functionType(returns = IntType)
        val moduleName = listOf(Identifier("Example"))
        val functionReference = export("main")
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            valueReference.nodeId to valueTarget
        ))
        val moduleType = moduleType(fields = mapOf(function.name.value to functionType))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                exports = listOf(functionReference),
                body = listOf(valueDeclaration, function)
            ),
            type = moduleType,
            references = references
        )

        val moduleSet = ModuleSet(listOf(module))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(moduleName),
                ModuleLoad(moduleName),
                FieldAccess(Identifier("main"), receiverType = moduleType),
                Call(positionalArgumentCount = 0, namedArgumentNames = listOf())
            ),
            type = IntType,
            moduleSet = moduleSet
        )

        assertThat(value, isInt(42))
    }

    @Test
    fun functionCanReferenceVariablesFromLaterInOuterScope() {
        val valueTarget = targetVariable("value")
        val valueDeclaration = valStatement(valueTarget, literalInt(42))
        val valueReference = variableReference("value")
        val function = function(
            name = "main",
            body = listOf(
                expressionStatementReturn(valueReference)
            )
        )
        val functionType = functionType(returns = IntType)
        val moduleName = listOf(Identifier("Example"))
        val functionReference = export("main")
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            valueReference.nodeId to valueTarget
        ))
        val moduleType = moduleType(fields = mapOf(function.name.value to functionType))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                exports = listOf(functionReference),
                body = listOf(function, valueDeclaration)
            ),
            type = moduleType,
            references = references
        )

        val moduleSet = ModuleSet(listOf(module))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(moduleName),
                ModuleLoad(moduleName),
                FieldAccess(Identifier("main"), receiverType = moduleType),
                Call(positionalArgumentCount = 0, namedArgumentNames = listOf())
            ),
            type = IntType,
            moduleSet = moduleSet
        )

        assertThat(value, isInt(42))
    }

    @Nested
    inner class VarargsTests {
        private val headParameter = parameter("head")
        private val tailParameter = parameter("tail")

        private val headReference = variableReference("head")
        private val tailReference = variableReference("tail")

        private val openParen = literalString("(")
        private val varargsDeclaration = varargsDeclaration(
            name = "tuple",
            cons = functionExpression(
                parameters = listOf(headParameter, tailParameter),
                body = binaryOperation(
                    BinaryOperator.ADD,
                    openParen,
                    binaryOperation(
                        BinaryOperator.ADD,
                        headReference,
                        binaryOperation(
                            BinaryOperator.ADD,
                            tailReference,
                            literalString(")")
                        )
                    )
                )
            ),
            nil = literalString("nil")
        )

        private val varargsReference = variableReference("tuple")

        private val references = ResolvedReferencesMap(mapOf(
            headReference.nodeId to headParameter,
            tailReference.nodeId to tailParameter,
            varargsReference.nodeId to varargsDeclaration
        ))
        private val types = createTypes(
            expressionTypes = mapOf(
                varargsReference.nodeId to varargsType(),
                openParen.nodeId to StringType,
                headReference.nodeId to StringType,
                tailReference.nodeId to StringType
            )
        )

        @Test
        fun callingVarargsWithNoArgumentsCreatesNil() {
            val call = call(
                receiver = varargsReference,
                positionalArguments = listOf()
            )

            val loader = loader(references = references, types = types)
            val instructions = persistentListOf<Instruction>()
                .addAll(loader.loadModuleStatement(varargsDeclaration))
                .addAll(loader.loadExpression(call))
            val value = executeInstructions(instructions, type = StringType)

            assertThat(value, isString("nil"))
        }

        @Test
        fun callingVarargsWithOneArgumentsCallsConsOnce() {
            val call = call(
                receiver = varargsReference,
                positionalArguments = listOf(literalString("42"))
            )

            val loader = loader(references = references, types = types)
            val instructions = persistentListOf<Instruction>()
                .addAll(loader.loadModuleStatement(varargsDeclaration))
                .addAll(loader.loadExpression(call))
            val value = executeInstructions(instructions, type = StringType)

            assertThat(value, isString("(42nil)"))
        }

        @Test
        fun callingVarargsWithTwoArgumentsCallsConsTwice() {
            val call = call(
                receiver = varargsReference,
                positionalArguments = listOf(literalString("42"), literalString("hello"))
            )

            val loader = loader(references = references, types = types)
            val instructions = persistentListOf<Instruction>()
                .addAll(loader.loadModuleStatement(varargsDeclaration))
                .addAll(loader.loadExpression(call))
            val value = executeInstructions(instructions, type = StringType)

            assertThat(value, isString("(42(hellonil))"))
        }
    }

    @Test
    fun partialCallCombinesPositionalArguments() {
        val parameter1 = parameter("first")
        val parameter2 = parameter("second")
        val parameterReference1 = variableReference("first")
        val parameterReference2 = variableReference("second")

        val function = function(
            name = "main",
            parameters = listOf(parameter1, parameter2),
            body = listOf(
                expressionStatementReturn(
                    binaryOperation(BinaryOperator.SUBTRACT, parameterReference1, parameterReference2)
                )
            )
        )
        val functionReference = variableReference("main")
        val partialCall = partialCall(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(1))
        )
        val call = call(
            receiver = partialCall,
            positionalArguments = listOf(literalInt(2))
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            parameterReference1.nodeId to parameter1,
            parameterReference2.nodeId to parameter2
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                partialCall.nodeId to functionType(
                    positionalParameters = listOf(IntType),
                    returns = IntType
                ),
                parameterReference1.nodeId to IntType
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(-1))
    }

    @Test
    fun partialCallCombinesNamedArguments() {
        val parameter1 = parameter("first")
        val parameter2 = parameter("second")
        val parameterReference1 = variableReference("first")
        val parameterReference2 = variableReference("second")

        val function = function(
            name = "main",
            namedParameters = listOf(parameter1, parameter2),
            body = listOf(
                expressionStatementReturn(
                    binaryOperation(BinaryOperator.SUBTRACT, parameterReference1, parameterReference2)
                )
            )
        )
        val functionReference = variableReference("main")
        val partialCall = partialCall(
            receiver = functionReference,
            namedArguments = listOf(callNamedArgument("second", literalInt(2)))
        )
        val call = call(
            receiver = partialCall,
            namedArguments = listOf(callNamedArgument("first", literalInt(1)))
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            parameterReference1.nodeId to parameter1,
            parameterReference2.nodeId to parameter2
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                partialCall.nodeId to functionType(
                    namedParameters = mapOf(Identifier("first") to IntType),
                    returns = IntType
                ),
                parameterReference1.nodeId to IntType
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions, type = IntType)

        assertThat(value, isInt(-1))
    }

    @Test
    fun moduleCanBeImported() {
        val exportingModuleName = listOf(Identifier("Exporting"))
        val exportNode = export("x")
        val valTarget = targetVariable("x")
        val exportingModuleType = moduleType(fields = mapOf("x" to IntType))
        val exportingModule = stubbedModule(
            name = exportingModuleName,
            node = module(
                exports = listOf(exportNode),
                body = listOf(
                    valStatement(valTarget, literalInt(42))
                )
            ),
            type = exportingModuleType,
            references = ResolvedReferencesMap(mapOf(
                exportNode.nodeId to valTarget
            ))
        )

        val importingModuleName = listOf(Identifier("Importing"))
        val importTarget = targetVariable("x")
        val importTargetFields = targetFields(listOf(fieldName("x") to importTarget))
        val importNode = import(
            importTargetFields,
            ImportPath.absolute(exportingModuleName.map(Identifier::value))
        )
        val reexportNode = export("x")
        val importingModuleType = moduleType(fields = mapOf("x" to IntType))
        val importingModule = stubbedModule(
            name = importingModuleName,
            node = module(
                imports = listOf(importNode),
                exports = listOf(reexportNode)
            ),
            type = importingModuleType,
            references = ResolvedReferencesMap(mapOf(
                reexportNode.nodeId to importTarget
            )),
            types = createTypes(targetTypes = mapOf(importTargetFields.nodeId to exportingModule.type))
        )

        val moduleSet = ModuleSet(listOf(exportingModule, importingModule))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(importingModuleName),
                ModuleLoad(importingModuleName),
                FieldAccess(Identifier("x"), receiverType = importingModuleType)
            ),
            type = IntType,
            moduleSet = moduleSet
        )

        assertThat(value, isInt(42))
    }

    @Test
    fun moduleNameIsDefinedInEachModule() {
        val moduleName = listOf(Identifier("One"), Identifier("Two"), Identifier("Three"))
        val moduleNameReference = export("moduleName")
        val references = ResolvedReferencesMap(mapOf(
            moduleNameReference.nodeId to Builtins.moduleName
        ))
        val moduleType = moduleType(fields = mapOf("moduleName" to StringType))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                exports = listOf(moduleNameReference)
            ),
            type = moduleType,
            references = references
        )

        val moduleSet = ModuleSet(listOf(module))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(moduleName),
                ModuleLoad(moduleName),
                FieldAccess(Identifier("moduleName"), receiverType = moduleType)
            ),
            type = StringType,
            moduleSet = moduleSet
        )

        assertThat(value, isString("One.Two.Three"))
    }

    private fun evaluateExpression(node: ExpressionNode, type: Type, types: Types = createTypes()): IrValue {
        val instructions = loader(types = types).loadExpression(node)
        return executeInstructions(instructions, type = type)
    }

    private fun evaluateBlock(block: Block, type: Type, references: ResolvedReferences = ResolvedReferencesMap.EMPTY): IrValue {
        val instructions = loader(references = references).loadBlock(block)
        return executeInstructions(instructions, type = type)
    }

    private fun executeInstructions(instructions: List<Instruction>, type: Type, moduleSet: ModuleSet = ModuleSet(listOf())): IrValue {
        return environment.executeInstructions(instructions, type = type, moduleSet = moduleSet).value
    }

    private fun createTypes(
        expressionTypes: Map<Int, Type> = mapOf(),
        targetTypes: Map<Int, Type> = mapOf(),
        variableTypes: Map<Int, Type> = mapOf()
    ): Types {
        return TypesMap(
            discriminators = mapOf(),
            expressionTypes = expressionTypes,
            functionTypes = mapOf(),
            targetTypes = targetTypes,
            variableTypes = variableTypes
        )
    }

    private fun stubbedModule(
        name: ModuleName,
        node: ModuleNode,
        type: ModuleType,
        references: ResolvedReferences = ResolvedReferencesMap.EMPTY,
        types: Types = EMPTY_TYPES
    ): Module.Shed {
        return Module.Shed(
            name = name,
            type = type,
            types = types,
            references = references,
            node = node
        )
    }
}
