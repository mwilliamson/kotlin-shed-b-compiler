package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.InvalidOperationError
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.types.IntType

class TypeCheckBinaryOperationTests {
    @Test
    fun addingTwoIntegersReturnsInteger() {
        val node = binaryOperation(BinaryOperator.ADD, literalInt(1), literalInt(2))
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun addWithLeftBooleanOperandThrowsTypeError() {
        val node = binaryOperation(BinaryOperator.ADD, literalBool(true), literalInt(2))
        assertThat(
            { inferType(node, emptyTypeContext()) },
            throws(allOf(
                has(InvalidOperationError::operator, equalTo(BinaryOperator.ADD)),
                has(InvalidOperationError::operands, isSequence(isBoolType, isIntType))
            ))
        )
    }

    @Test
    fun addWithRightBooleanOperandThrowsTypeError() {
        val node = binaryOperation(BinaryOperator.ADD, literalInt(2), literalBool(true))
        assertThat(
            { inferType(node, emptyTypeContext()) },
            throws(allOf(
                has(InvalidOperationError::operator, equalTo(BinaryOperator.ADD)),
                has(InvalidOperationError::operands, isSequence(isIntType, isBoolType))
            ))
        )
    }

    @Test
    fun integerSubtractionOperationReturnsInteger() {
        val node = binaryOperation(BinaryOperator.SUBTRACT, literalInt(), literalInt())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, isIntType)
    }

    @Test
    fun integerMultiplicationOperationReturnsInteger() {
        val node = binaryOperation(BinaryOperator.MULTIPLY, literalInt(), literalInt())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, isIntType)
    }

    @Test
    fun integerEqualityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.EQUALS, literalInt(1), literalInt(2))
        val type = inferType(node, emptyTypeContext())
        assertThat(type, isBoolType)
    }

    @Test
    fun integerInequalityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, literalInt(1), literalInt(2))
        val type = inferType(node, emptyTypeContext())
        assertThat(type, isBoolType)
    }

    @Test
    fun stringEqualityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.EQUALS, literalString(), literalString())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun stringInequalityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, literalString(), literalString())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun stringAdditionOperationReturnsString() {
        val node = binaryOperation(BinaryOperator.ADD, literalString(), literalString())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isStringType))
    }

    @TestFactory
    fun unicodeScalarComparisonOperationReturnsBoolean(): List<DynamicTest> {
        return listOf(
            BinaryOperator.EQUALS,
            BinaryOperator.NOT_EQUAL,
            BinaryOperator.LESS_THAN,
            BinaryOperator.LESS_THAN_OR_EQUAL,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.GREATER_THAN_OR_EQUAL
        ).map { operator -> DynamicTest.dynamicTest("char $operator operation returns boolean", {
            val node = binaryOperation(operator, literalUnicodeScalar(), literalUnicodeScalar())
            val type = inferType(node, emptyTypeContext())
            assertThat(type, cast(isBoolType))
        }) }
    }

    @Test
    fun booleanEqualityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.EQUALS, literalBool(), literalBool())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun booleanInequalityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, literalBool(), literalBool())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun symbolEqualityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.EQUALS, symbolName("`x"), symbolName("`y"))
        val type = inferType(node, typeContext(moduleName = listOf("A")))
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun symbolInequalityOperationReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.NOT_EQUAL, symbolName("`x"), symbolName("`y"))
        val type = inferType(node, typeContext(moduleName = listOf("A")))
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun logicalAndReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.AND, literalBool(), literalBool())
        val type = inferType(node, typeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun logicalOrReturnsBoolean() {
        val node = binaryOperation(BinaryOperator.OR, literalBool(), literalBool())
        val type = inferType(node, typeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun leftOperandIsUnaliased() {
        val reference = variableReference("x")
        val node = binaryOperation(BinaryOperator.ADD, reference, literalInt())

        val context = typeContext(referenceTypes = mapOf(
            reference to typeAlias("Count", IntType)
        ))
        val type = inferType(node, context)

        assertThat(type, isIntType)
    }

    @Test
    fun rightOperandIsUnaliased() {
        val reference = variableReference("x")
        val node = binaryOperation(BinaryOperator.ADD, literalInt(), reference)

        val context = typeContext(referenceTypes = mapOf(
            reference to typeAlias("Count", IntType)
        ))
        val type = inferType(node, context)

        assertThat(type, isIntType)
    }
}
