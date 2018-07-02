package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.frontend.tests.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.InvalidOperationError
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.types.*

class TypeCheckExpressionTests {
    @Test
    fun unitLiteralIsTypedAsUnit() {
        val node = literalUnit()
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(UnitType)))
    }

    @Test
    fun booleanLiteralIsTypedAsBoolean() {
        val node = literalBool(true)
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(BoolType)))
    }

    @Test
    fun integerLiteralIsTypedAsInteger() {
        val node = literalInt(42)
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun stringLiteralIsTypedAsString() {
        val node = literalString("<string>")
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(StringType)))
    }

    @Test
    fun characterLiteralIsTypedAsChar() {
        val node = literalChar()
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(CharType)))
    }

    @Test
    fun symbolIsTypedAsSymbol() {
        val node = symbolName("@blah")
        val type = inferType(node, typeContext(moduleName = listOf("Some", "Module")))
        assertThat(type, isType(SymbolType(listOf("Some", "Module"), "@blah")))
    }

    @Test
    fun variableReferenceTypeIsRetrievedFromContext() {
        val node = variableReference("x")
        val type = inferType(node, typeContext(referenceTypes = mutableMapOf(node to IntType)))
        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun whenVariableHasNoTypeThenCompilerErrorIsThrown() {
        val reference = variableReference("x")
        val declaration = valStatement("x")

        assertThat(
            { inferType(
                reference,
                newTypeContext(
                    moduleName = null,
                    nodeTypes = mutableMapOf(),
                    resolvedReferences = ResolvedReferencesMap(mapOf(reference.nodeId to declaration)),
                    getModule = { moduleName -> throw UnsupportedOperationException() }
                )
            ) },
            throwsCompilerError("type of x is unknown")
        )
    }

    @Test
    fun addingTwoIntegersReturnsInteger() {
        val node = binaryOperation(Operator.ADD, literalInt(1), literalInt(2))
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun addWithLeftBooleanOperandThrowsTypeError() {
        val node = binaryOperation(Operator.ADD, literalBool(true), literalInt(2))
        assertThat(
            { inferType(node, emptyTypeContext()) },
            throws(allOf(
                has(InvalidOperationError::operator, equalTo(Operator.ADD)),
                has(InvalidOperationError::operands, isSequence(isBoolType, isIntType))
            ))
        )
    }

    @Test
    fun addWithRightBooleanOperandThrowsTypeError() {
        val node = binaryOperation(Operator.ADD, literalInt(2), literalBool(true))
        assertThat(
            { inferType(node, emptyTypeContext()) },
            throws(allOf(
                has(InvalidOperationError::operator, equalTo(Operator.ADD)),
                has(InvalidOperationError::operands, isSequence(isIntType, isBoolType))
            ))
        )
    }

    @Test
    fun integerSubtractionOperationReturnsInteger() {
        val node = binaryOperation(Operator.SUBTRACT, literalInt(), literalInt())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, isIntType)
    }

    @Test
    fun integerMultiplicationOperationReturnsInteger() {
        val node = binaryOperation(Operator.MULTIPLY, literalInt(), literalInt())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, isIntType)
    }

    @Test
    fun integerEqualityOperationReturnsBoolean() {
        val node = binaryOperation(Operator.EQUALS, literalInt(1), literalInt(2))
        val type = inferType(node, emptyTypeContext())
        assertThat(type, isBoolType)
    }

    @Test
    fun stringEqualityOperationReturnsBoolean() {
        val node = binaryOperation(Operator.EQUALS, literalString(), literalString())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun stringAdditionOperationReturnsString() {
        val node = binaryOperation(Operator.ADD, literalString(), literalString())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isStringType))
    }

    @TestFactory
    fun charComparisonOperationReturnsBoolean(): List<DynamicTest> {
        return listOf(
            Operator.EQUALS,
            Operator.LESS_THAN,
            Operator.LESS_THAN_OR_EQUAL,
            Operator.GREATER_THAN,
            Operator.GREATER_THAN_OR_EQUAL
        ).map { operator -> DynamicTest.dynamicTest("char $operator operation returns boolean", {
            val node = binaryOperation(operator, literalChar(), literalChar())
            val type = inferType(node, emptyTypeContext())
            assertThat(type, cast(isBoolType))
        }) }
    }

    @Test
    fun booleanEqualityOperationReturnsBoolean() {
        val node = binaryOperation(Operator.EQUALS, literalBool(), literalBool())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun symbolEqualityOperationReturnsBoolean() {
        val node = binaryOperation(Operator.EQUALS, symbolName("@x"), symbolName("@y"))
        val type = inferType(node, typeContext(moduleName = listOf("A")))
        assertThat(type, cast(isBoolType))
    }

    @Test
    fun functionExpressionHasFunctionType() {
        val intReference = staticReference("Int")
        val unitReference = staticReference("Unit")
        val node = functionExpression(
            parameters = listOf(parameter(type = intReference)),
            returnType = unitReference
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                intReference to MetaType(IntType),
                unitReference to MetaType(UnitType)
            )
        )
        val type = inferType(node, typeContext)
        assertThat(type, isFunctionType(
            positionalParameters = isSequence(isIntType),
            returnType = isUnitType
        ))
    }
}
