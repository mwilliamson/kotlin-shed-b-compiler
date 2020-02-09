package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.Types
import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Block
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
                shapeReference.nodeId to MetaType(shapeType)
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
                shapeReference.nodeId to MetaType(shapeType),
                firstReference.nodeId to IntType
            ),
            targetTypes = mapOf(
                fieldsTarget.nodeId to shapeType
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
            branches = listOf(branch1, branch2, branch3, branch4)
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
                shape2Reference.nodeId to MetaType(shapeType())
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

    private fun evaluateExpression(node: ExpressionNode, type: Type, types: Types = createTypes()): IrValue {
        val instructions = loader(types = types).loadExpression(node)
        return executeInstructions(instructions, type = type)
    }

    private fun evaluateBlock(block: Block, type: Type, references: ResolvedReferences): IrValue {
        val instructions = loader(references = references).loadBlock(block)
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
