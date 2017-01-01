package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*

class TypeCheckReturnTests {
    @Test
    fun whenReturnValueMatchesExpectedTypeThenReturnTypeChecks() {
        val node = returns(literalInt(1))
        typeCheck(node, typeContext(returnType = IntType))
    }

    @Test
    fun whenNoReturnValueIsExpectedThenReturnDoesNotTypeCheck() {
        val node = returns(literalInt(1))
        assertThat(
            { typeCheck(node, typeContext(returnType = null)) },
            throws<ReturnOutsideOfFunctionError>()
        )
    }

    @Test
    fun whenReturnValueDoesNotMatchExpectedTypeThenReturnDoesNotTypeCheck() {
        val node = returns(literalInt(1))
        assertThat(
            { typeCheck(node, typeContext(returnType = BoolType)) },
            throws(allOf(
                has(UnexpectedTypeError::expected, cast(equalTo(BoolType))),
                has(UnexpectedTypeError::actual, cast(equalTo(IntType)))
            ))
        )
    }
}
