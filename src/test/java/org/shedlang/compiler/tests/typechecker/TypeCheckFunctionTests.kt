package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.isFunctionType
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

    @Test
    fun functionArgumentsAreAddedToScope() {
        val node = function(
            arguments = listOf(argument(name = "x", type = typeReference("Int"))),
            returnType = typeReference("Int"),
            body = listOf(returns(variableReference("x")))
        )
        typeCheck(node, typeContext(variables = mapOf(Pair("Int", MetaType(IntType)))))
    }

    @Test
    fun functionArgumentsCanShadowExistingVariables() {
        val node = function(
            arguments = listOf(argument(name = "x", type = typeReference("Int"))),
            returnType = typeReference("Int"),
            body = listOf(returns(variableReference("x")))
        )
        typeCheck(
            node,
            typeContext(variables = mapOf(
                "Int" to MetaType(IntType),
                "x" to BoolType
            ))
        )
    }

    @Test
    fun signatureOfFunctionIsDeterminedFromArgumentsAndReturnType() {
        val node = function(
            arguments = listOf(
                argument(name = "x", type = typeReference("Int")),
                argument(name = "y", type = typeReference("Bool"))
            ),
            returnType = typeReference("Int"),
            body = listOf(returns(variableReference("x")))
        )
        val signature = inferType(
            node,
            typeContext(variables = mapOf(
                "Int" to MetaType(IntType),
                "Bool" to MetaType(BoolType)
            ))
        )
        assertThat(signature, isFunctionType(
            arguments = equalTo(listOf(IntType, BoolType)),
            returnType = equalTo(IntType)
        ))
    }
}
