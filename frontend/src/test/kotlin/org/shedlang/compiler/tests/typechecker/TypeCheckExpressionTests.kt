package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.BinaryOperationNode
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*

class TypeCheckExpressionTests {
    @Test
    fun booleanLiteralIsTypedAsInteger() {
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
    fun variableReferenceTypeIsRetrievedFromContext() {
        val node = variableReference("x")
        val type = inferType(node, typeContext(referenceTypes = mutableMapOf(node to IntType)))
        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun addingTwoIntegersReturnsInteger() {
        val node = BinaryOperationNode(Operator.ADD, literalInt(1), literalInt(2), anySource())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(IntType)))
    }

    @Test
    fun addWithLeftBooleanOperandThrowsTypeError() {
        val node = BinaryOperationNode(Operator.ADD, literalBool(true), literalInt(2), anySource())
        assertThat(
            { inferType(node, emptyTypeContext()) },
            throws(allOf(
                has(UnexpectedTypeError::expected, cast(equalTo(IntType))),
                has(UnexpectedTypeError::actual, cast(equalTo(BoolType)))
            ))
        )
    }

    @Test
    fun addWithRightBooleanOperandThrowsTypeError() {
        val node = BinaryOperationNode(Operator.ADD, literalInt(2), literalBool(true), anySource())
        assertThat(
            { inferType(node, emptyTypeContext()) },
            throws(allOf(
                has(UnexpectedTypeError::expected, cast(equalTo(IntType))),
                has(UnexpectedTypeError::actual, cast(equalTo(BoolType)))
            ))
        )
    }

    @Test
    fun equalityOperationReturnsBoolean() {
        val node = BinaryOperationNode(Operator.EQUALS, literalInt(1), literalInt(2), anySource())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(BoolType)))
    }
}
