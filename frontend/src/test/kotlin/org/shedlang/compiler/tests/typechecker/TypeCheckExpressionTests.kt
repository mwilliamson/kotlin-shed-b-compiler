package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.BinaryOperator
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
    fun unicodeScalarLiteralIsTypedAsUnicodeScalar() {
        val node = literalUnicodeScalar()
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(UnicodeScalarType)))
    }

    @Test
    fun symbolIsTypedAsSymbol() {
        val node = symbolName("`blah")
        val type = inferType(node, typeContext(moduleName = listOf("Some", "Module")))
        assertThat(type, isType(symbolType(listOf("Some", "Module"), "`blah")))
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
        val declaration = variableBinder("x")

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
