package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.testing.literalInt
import org.shedlang.compiler.testing.returns
import org.shedlang.compiler.typechecker.ReturnOutsideOfFunctionError
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.IntType

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
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }
}
