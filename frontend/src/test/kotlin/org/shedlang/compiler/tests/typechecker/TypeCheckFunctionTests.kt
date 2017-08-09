package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.anything
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.*

class TypeCheckFunctionTests {
    @Test
    fun typeParameterTypeIsAdded() {
        val unitReference = typeReference("Unit")

        val typeParameter = typeParameter("T")
        val typeParameterReference = typeReference("T")
        val node = function(
            typeParameters = listOf(typeParameter),
            arguments = listOf(argument(type = typeParameterReference)),
            returnType = unitReference
        )
        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameter),
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        )

        typeCheck(node, typeContext)
        typeContext.undefer()

        assertThat(typeContext.typeOf(typeParameter), isMetaType(
            cast(has(TypeParameter::name, equalTo("T")))
        ))
        assertThat(
            typeContext.typeOf(typeParameterReference),
            equalTo(typeContext.typeOf(typeParameter))
        )
        assertThat(
            typeContext.typeOf(node),
            cast(
                has(FunctionType::typeParameters, isSequence(
                    has(TypeParameter::name, equalTo("T"))
                ))
            )
        )
    }

    @Test
    fun bodyOfFunctionIsTypeChecked() {
        val unit = typeReference("Unit")
        val typeContext = typeContext(referenceTypes = mapOf(unit to MetaType(UnitType)))

        assertStatementIsTypeChecked(fun(badStatement) {
            val node = function(
                returnType = unit,
                body = listOf(badStatement)
            )
            typeCheck(node, typeContext)
            typeContext.undefer()
        })
    }

    @Test
    fun returnStatementsInBodyMustReturnCorrectType() {
        val intType = typeReference("Int")
        val typeContext = typeContext(referenceTypes = mapOf(intType to MetaType(IntType)))
        val node = function(
            returnType = intType,
            body = listOf(returns(literalBool(true)))
        )
        typeCheck(node, typeContext)

        assertThat(
            { typeContext.undefer() },
            throwsUnexpectedType(expected = IntType, actual = BoolType)
        )
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
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to MetaType(IntType)),
            references = mapOf(argumentReference to argument)
        )
        typeCheck(node, typeContext)
        typeContext.undefer()
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
        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType),
            boolType to MetaType(BoolType)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isFunctionType(
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
        val typeContext = typeContext(referenceTypes = mapOf(
            unitType to MetaType(UnitType),
            effect to EffectType(IoEffect)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isFunctionType(
            arguments = anything,
            returnType = anything,
            effects = isSequence(cast(equalTo(IoEffect)))
        ))
    }

    @Test
    fun effectsAreAddedToBodyContext() {
        val unitType = typeReference("Unit")
        val effect = variableReference("!io")
        val functionReference = variableReference("f")

        val node = function(
            returnType = unitType,
            effects = listOf(effect),
            body = listOf(
                expressionStatement(call(functionReference))
            )
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                effects = setOf(IoEffect),
                returns = UnitType
            ),
            unitType to MetaType(UnitType),
            effect to EffectType(IoEffect)
        ))
        typeCheck(node, typeContext)
        // TODO: come up with a way of ensuring undefer() is eventually called
        typeContext.undefer()
    }
}
