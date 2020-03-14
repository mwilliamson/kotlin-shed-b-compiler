package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.UnaryOperator
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.InvalidUnaryOperationError
import org.shedlang.compiler.typechecker.inferType

class TypeCheckUnaryOperationTests {
    @Test
    fun negatingBooleanReturnsBoolean() {
        val node = unaryOperation(UnaryOperator.NOT, literalBool(true))
        val type = inferType(node, typeContext())
        assertThat(type, isBoolType)
    }

    @Test
    fun negatingNonBooleansThrowsError() {
        val node = unaryOperation(UnaryOperator.NOT, literalInt(1))
        assertThat(
            { inferType(node, typeContext()) },
            throws(allOf(
                has(InvalidUnaryOperationError::operator, equalTo(UnaryOperator.NOT)),
                has(InvalidUnaryOperationError::actualOperandType, isIntType)
            ))
        )
    }

    @Test
    fun unaryMinusIntReturnsInt() {
        val node = unaryOperation(UnaryOperator.MINUS, literalInt())
        val type = inferType(node, typeContext())
        assertThat(type, isIntType)
    }

    @Test
    fun unaryMinusNonIntThrowsError() {
        val node = unaryOperation(UnaryOperator.MINUS, literalBool())
        assertThat(
            { inferType(node, typeContext()) },
            throws(allOf(
                has(InvalidUnaryOperationError::operator, equalTo(UnaryOperator.MINUS)),
                has(InvalidUnaryOperationError::actualOperandType, isBoolType)
            ))
        )
    }
}
