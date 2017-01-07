package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.typechecker.BoolType
import org.shedlang.compiler.typechecker.IntType
import org.shedlang.compiler.typechecker.MetaType
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckFunctionTests {
    @Test
    fun returnStatementsInBodyMustReturnCorrectType() {
        assertThat({
            typeCheck(function(
                returnType = typeReference("Int"),
                body = listOf(returns(literalBool(true)))
            ), typeContext(variables = mapOf(Pair("Int", MetaType(IntType)))))
        }, throwsUnexpectedType(expected = IntType, actual = BoolType))
    }
}
