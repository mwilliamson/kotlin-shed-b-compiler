package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.anything
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*

class TypeCheckFunctionTests {
    @Test
    fun bodyOfFunctionIsTypeChecked() {
        val unit = typeReference("Unit")
        assertStatementIsTypeChecked({ badStatement ->
            typeCheck(function(
                returnType = unit,
                body = listOf(badStatement)
            ), typeContext(referenceTypes = mapOf(unit to MetaType(UnitType))))
        })
    }

    @Test
    fun returnStatementsInBodyMustReturnCorrectType() {
        val intType = typeReference("Int")
        assertThat({
            typeCheck(function(
                returnType = intType,
                body = listOf(returns(literalBool(true)))
            ), typeContext(referenceTypes = mapOf(intType to MetaType(IntType))))
        }, throwsUnexpectedType(expected = IntType, actual = BoolType))
    }

    @Test
    fun functionArgumentsAreTyped() {
        val intType = typeReference("Int")
        val argument = argument(name = "x", type = intType)
        val argumentReference = variableReference("x")
        val node = function(
            arguments = listOf(argument),
            returnType = intType,
            body = listOf(returns(argumentReference))
        )
        typeCheck(node, typeContext(
            referenceTypes = mapOf(intType to MetaType(IntType)),
            references = mapOf(argumentReference to argument)
        ))
    }

    @Test
    fun signatureOfFunctionIsDeterminedFromArgumentsAndReturnType() {
        val intType = typeReference("Int")
        val boolType = typeReference("Bool")
        val node = function(
            arguments = listOf(
                argument(name = "x", type = intType),
                argument(name = "y", type = boolType)
            ),
            returnType = intType,
            body = listOf(returns(literalInt()))
        )
        val signature = inferType(
            node,
            typeContext(referenceTypes = mapOf(
                intType to MetaType(IntType),
                boolType to MetaType(BoolType)
            ))
        )
        assertThat(signature, isFunctionType(
            arguments = equalTo(listOf(IntType, BoolType)),
            returnType = equalTo(IntType)
        ))
    }

    @Test
    fun effectsAreIncludedInSignature() {
        val unitType = typeReference("Unit")
        val effect = variableReference("!io")

        val node = function(
            returnType = unitType,
            effects = listOf(effect)
        )
        val signature = inferType(
            node,
            typeContext(referenceTypes = mapOf(
                unitType to MetaType(UnitType),
                effect to EffectType(IoEffect)
            ))
        )
        assertThat(signature, isFunctionType(
            arguments = anything,
            returnType = anything,
            effects = isSequence(cast(equalTo(IoEffect)))
        ))
    }
}
