package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.typechecker.*

class TypeCheckFunctionTests {
    @Test
    fun bodyOfFunctionIsTypeChecked() {
        assertStatementIsTypeChecked({ badStatement ->
            typeCheck(function(
                returnType = typeReference("Unit"),
                body = listOf(badStatement)
            ), typeContext(variables = mapOf(Pair("Unit", MetaType(UnitType)))))
        })
    }

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
