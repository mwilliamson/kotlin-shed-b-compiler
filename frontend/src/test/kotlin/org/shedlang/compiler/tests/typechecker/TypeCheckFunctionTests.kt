package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionDefinitionNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckFunctionTests {
    @Test
    fun typeParameterTypeIsAdded() {
        val unitReference = typeLevelReference("Unit")

        val typeParameter = typeParameter("T")
        val typeParameterReference = typeLevelReference("T")
        val parameter = parameter(type = typeParameterReference)
        val node = function(
            typeLevelParameters = listOf(typeParameter),
            parameters = listOf(parameter),
            returnType = unitReference
        )
        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameter),
            referenceTypes = mapOf(unitReference to UnitMetaType)
        )

        typeCheckFunctionDefinition(node, typeContext)
        typeContext.undefer()

        assertThat(typeContext.typeOf(typeParameter), isMetaType(
            cast(has(TypeParameter::name, isIdentifier("T")))
        ))
        assertThat(
            typeContext.typeOf(parameter),
            equalTo(metaTypeToType(typeContext.typeOf(typeParameter)))
        )
        assertThat(
            typeContext.typeOf(node),
            cast(
                has(FunctionType::typeLevelParameters, isSequence(
                    cast(has(TypeParameter::name, isIdentifier("T")))
                ))
            )
        )
    }

    @Test
    fun effectParameterTypeIsAdded() {
        val unitReference = typeLevelReference("Unit")

        val effectParameter = effectParameterDeclaration("E")
        val effectParameterReference = typeLevelReference("E")
        val node = function(
            typeLevelParameters = listOf(effectParameter),
            parameters = listOf(),
            effect = effectParameterReference,
            returnType = unitReference
        )
        val typeContext = typeContext(
            references = mapOf(effectParameterReference to effectParameter),
            referenceTypes = mapOf(unitReference to UnitMetaType)
        )

        typeCheckFunctionDefinition(node, typeContext)
        typeContext.undefer()

        assertThat(typeContext.typeOf(effectParameter), isEffectType(
            cast(has(EffectParameter::name, isIdentifier("E")))
        ))
        assertThat(
            (typeContext.typeOf(node) as FunctionType).effect,
            equalTo((typeContext.typeOf(effectParameter) as TypeLevelValueType).value)
        )
        assertThat(
            typeContext.typeOf(node),
            cast(
                has(FunctionType::typeLevelParameters, isSequence(
                    cast(has(EffectParameter::name, isIdentifier("E")))
                ))
            )
        )
    }

    @Test
    fun bodyOfFunctionIsTypeChecked() {
        val unit = typeLevelReference("Unit")
        val typeContext = typeContext(referenceTypes = mapOf(unit to UnitMetaType))

        assertStatementIsTypeChecked(fun(badStatement) {
            val node = function(
                returnType = unit,
                body = listOf(badStatement)
            )
            typeCheckFunctionDefinition(node, typeContext)
            typeContext.undefer()
        })
    }

    @Test
    fun finalStatementInBodyMustReturnCorrectType() {
        val intType = typeLevelReference("Int")
        val typeContext = typeContext(referenceTypes = mapOf(intType to IntMetaType))
        val node = function(
            returnType = intType,
            body = listOf(expressionStatementReturn(literalBool(true)))
        )

        assertThat(
            { typeCheckFunctionDefinition(node, typeContext) },
            throwsUnexpectedType(expected = IntType, actual = BoolType)
        )
    }

    @Test
    fun positionalParametersAreTyped() {
        val intType = typeLevelReference("Int")
        val parameter = parameter(name = "x", type = intType)
        val parameterReference = variableReference("x")
        val node = function(
            parameters = listOf(parameter),
            returnType = intType,
            body = listOf(expressionStatementReturn(parameterReference))
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to IntMetaType),
            references = mapOf(parameterReference to parameter)
        )
        typeCheckFunctionDefinition(node, typeContext)
        typeContext.undefer()

        assertThat(
            typeContext.typeOf(node),
            isFunctionType(
                positionalParameters = isSequence(isIntType),
                namedParameters = isMap()
            )
        )
    }

    @Test
    fun givenThereIsNoTypeHintWhenPositionalParameterHasNoTypeThenErrorIsThrown() {
        val intType = typeLevelReference("Int")
        val parameter = parameter(name = "x", type = null)
        val parameterReference = variableReference("x")
        val node = function(
            parameters = listOf(parameter),
            returnType = intType,
            body = listOf(expressionStatementReturn(parameterReference))
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to IntMetaType),
            references = mapOf(parameterReference to parameter)
        )

        val typeCheck = {
            typeCheckFunction(node, typeContext)
            typeContext.undefer()
        }

        assertThat(
            typeCheck,
            throwsException(
                has(MissingParameterTypeError::message, present(equalTo("Missing type for parameter x"))),
            ),
        )
    }

    @Test
    fun givenThereIsATypeHintWhenPositionalParameterHasNoTypeThenHintIsUsed() {
        val intType = typeLevelReference("Int")
        val parameter = parameter(name = "x", type = null)
        val parameterReference = variableReference("x")
        val node = function(
            parameters = listOf(parameter),
            returnType = intType,
            body = listOf(expressionStatementReturn(parameterReference))
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to IntMetaType),
            references = mapOf(parameterReference to parameter)
        )

        val functionType = typeCheckFunction(node, typeContext, hint = functionType(positionalParameters = listOf(IntType)))
        typeContext.undefer()

        assertThat(
            functionType,
            isFunctionType(
                positionalParameters = isSequence(isIntType),
                namedParameters = isMap()
            )
        )
    }

    @Test
    fun namedParametersAreTyped() {
        val intType = typeLevelReference("Int")
        val parameter = parameter(name = "x", type = intType)
        val parameterReference = variableReference("x")
        val node = function(
            namedParameters = listOf(parameter),
            returnType = intType,
            body = listOf(expressionStatementReturn(parameterReference))
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to IntMetaType),
            references = mapOf(parameterReference to parameter)
        )
        typeCheckFunctionDefinition(node, typeContext)
        typeContext.undefer()

        assertThat(
            typeContext.typeOf(node),
            isFunctionType(
                positionalParameters = isSequence(),
                namedParameters = isMap(Identifier("x") to isIntType)
            )
        )
    }

    @Test
    fun givenThereIsNoTypeHintWhenNamedParameterHasNoTypeThenErrorIsThrown() {
        val intType = typeLevelReference("Int")
        val parameter = parameter(name = "x", type = null)
        val parameterReference = variableReference("x")
        val node = function(
            namedParameters = listOf(parameter),
            returnType = intType,
            body = listOf(expressionStatementReturn(parameterReference))
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to IntMetaType),
            references = mapOf(parameterReference to parameter)
        )

        val typeCheck = {
            typeCheckFunction(node, typeContext)
            typeContext.undefer()
        }

        assertThat(
            typeCheck,
            throwsException(
                has(MissingParameterTypeError::message, present(equalTo("Missing type for parameter x"))),
            ),
        )
    }

    @Test
    fun givenThereIsATypeHintWhenNamedParameterHasNoTypeThenHintIsUsed() {
        val intType = typeLevelReference("Int")
        val parameter = parameter(name = "x", type = null)
        val parameterReference = variableReference("x")
        val node = function(
            namedParameters = listOf(parameter),
            returnType = intType,
            body = listOf(expressionStatementReturn(parameterReference))
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to IntMetaType),
            references = mapOf(parameterReference to parameter)
        )

        val hint = functionType(namedParameters = mapOf(Identifier("x") to IntType))
        val functionType = typeCheckFunction(node, typeContext, hint = hint)
        typeContext.undefer()

        assertThat(
            functionType,
            isFunctionType(
                positionalParameters = isSequence(),
                namedParameters = isMap(Identifier("x") to isIntType)
            )
        )
    }

    @Test
    fun signatureOfFunctionIsDeterminedFromArgumentsAndReturnType() {
        val intType = typeLevelReference("Int")
        val boolType = typeLevelReference("Bool")
        val node = function(
            parameters = listOf(
                parameter(name = "x", type = intType),
                parameter(name = "y", type = boolType)
            ),
            returnType = intType,
            body = listOf(expressionStatementReturn(literalInt()))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            intType to IntMetaType,
            boolType to BoolMetaType
        ))
        typeCheckFunctionDefinition(node, typeContext)
        assertThat(typeContext.typeOf(node), isFunctionType(
            positionalParameters = equalTo(listOf(IntType, BoolType)),
            returnType = equalTo(IntType)
        ))
    }

    @Test
    fun effectsAreIncludedInSignature() {
        val unitType = typeLevelReference("Unit")
        val effect = typeLevelReference("!Io")

        val node = function(
            returnType = unitType,
            effect = effect
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            unitType to UnitMetaType,
            effect to TypeLevelValueType(IoEffect)
        ))
        typeCheckFunctionDefinition(node, typeContext)
        assertThat(typeContext.typeOf(node), isFunctionType(
            positionalParameters = anything,
            returnType = anything,
            effect = cast(equalTo(IoEffect))
        ))
    }

    @Test
    fun effectsAreAddedToBodyContext() {
        val unitType = typeLevelReference("Unit")
        val effect = typeLevelReference("!Io")
        val functionReference = variableReference("f")

        val node = function(
            returnType = unitType,
            effect = effect,
            body = listOf(
                expressionStatement(call(functionReference, hasEffect = true))
            )
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                effect = IoEffect,
                returns = UnitType
            ),
            unitType to UnitMetaType,
            effect to TypeLevelValueType(IoEffect)
        ))
        typeCheckFunctionDefinition(node, typeContext)
        // TODO: come up with a way of ensuring undefer() is eventually called
        typeContext.undefer()
    }

    @Test
    fun errorIsThrowIfBodyStatementsHaveUnspecifiedEffect() {
        val unitType = typeLevelReference("Unit")
        val functionReference = variableReference("f")

        val node = function(
            returnType = unitType,
            body = listOf(
                expressionStatement(call(functionReference, hasEffect = true))
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                unitType to UnitMetaType
            )
        )
        assertThat(
            {
                typeCheckFunctionDefinition(node, typeContext)
                typeContext.undefer()
            },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun whenFunctionHasInferredEffectThenEffectIsTakenFromHint() {
        val unitType = typeLevelReference("Unit")
        val effect = typeLevelReference("Io")
        val functionReference = variableReference("f")

        val node = functionExpression(
            returnType = unitType,
            effect = functionEffectInfer(),
            body = listOf(
                expressionStatement(call(functionReference, hasEffect = true))
            )
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            functionReference to functionType(
                effect = IoEffect,
                returns = UnitType
            ),
            unitType to UnitMetaType,
            effect to TypeLevelValueType(IoEffect)
        ))
        inferType(node, typeContext, hint = functionType(effect = IoEffect))
        // TODO: come up with a way of ensuring undefer() is eventually called
        typeContext.undefer()
    }

    @Test
    fun effectsAreNotInheritedByNestedFunctionBodyStatements() {
        val unitType = typeLevelReference("Unit")
        val functionReference = variableReference("f")

        val node = function(
            returnType = unitType,
            body = listOf(
                expressionStatement(call(functionReference, hasEffect = true))
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                unitType to UnitMetaType
            ),
            effect = IoEffect
        )
        assertThat(
            {
                typeCheckFunctionDefinition(node, typeContext)
                typeContext.undefer()
            },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun effectsAreNotInheritedByNestedFunctionBodyExpression() {
        val unitType = typeLevelReference("Unit")
        val functionReference = variableReference("f")

        val node = functionExpression(
            returnType = unitType,
            body = call(functionReference, hasEffect = true)
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                unitType to UnitMetaType
            ),
            effect = IoEffect
        )
        assertThat(
            {
                inferType(node, typeContext)
                typeContext.undefer()
            },
            throws(has(UnhandledEffectError::effect, cast(equalTo(IoEffect))))
        )
    }

    @Test
    fun whenExpressionBodyDoesNotMatchReturnTypeThenErrorIsThrown() {
        val intType = typeLevelReference("Int")
        val node = functionExpression(
            parameters = listOf(),
            returnType = intType,
            body = literalBool()
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to IntMetaType)
        )
        assertThat(
            {
                inferType(node, typeContext)
                typeContext.undefer()
            },
            throwsUnexpectedType(expected = cast(isIntType), actual = isBoolType)
        )
    }

    @Test
    fun whenExplicitReturnTypeIsMissingThenReturnTypeIsTypeOfExpressionBody() {
        val node = functionExpression(
            parameters = listOf(),
            returnType = null,
            body = literalBool()
        )
        assertThat(
            inferType(node, typeContext()),
            isFunctionType(returnType = isBoolType)
        )
    }

    @Test
    fun explicitReturnTypeIsUsedAsReturnTypeInsteadOfExpressionBodyType() {
        val anyReference = typeLevelReference("Any")
        val node = functionExpression(
            parameters = listOf(),
            returnType = anyReference,
            body = literalBool()
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(anyReference to AnyMetaType)
        )
        assertThat(
            inferType(node, typeContext),
            isFunctionType(returnType = isAnyType)
        )
    }

    @Test
    fun whenExplicitReturnTypeIsMissingThenReturnTypeCanBeInferredFromContext() {
        val source = object: Source {
            override fun describe(): String {
                return "return value source"
            }
        }
        val returnValue = literalBool()
        val node = functionExpression(
            returnType = null,
            body = listOf(expressionStatementReturn(returnValue, source = source))
        )
        val functionReference = variableReference("f")
        val call = call(
            receiver = functionReference,
            positionalArguments = listOf(node)
        )
        val receiverType = functionType(
            positionalParameters = listOf(
                functionType(returns = IntType)
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to receiverType)
        )
        assertThat(
            { inferCallType(call, typeContext); typeContext.undefer() },
            throwsUnexpectedType(
                expected = cast(isFunctionType(returnType = isIntType)),
                actual = isFunctionType(returnType = isBoolType),
            )
            // TODO: push the return type in?
            //throwsUnexpectedType(expected = cast(isIntType), actual = isBoolType, source = equalTo(source))
        )
    }

    @Test
    fun canTypeCheckFunctionDefinitionAsModuleStatement() {
        val unitReference = typeLevelReference("Unit")
        val node = function(
            returnType = unitReference
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(unitReference to UnitMetaType)
        )

        typeCheckModuleStatementAllPhases(node, typeContext)
        typeContext.undefer()
        assertThat(
            typeContext.typeOf(node),
            isFunctionType()
        )
    }

    @Test
    fun canTypeCheckFunctionDefinitionAsFunctionStatement() {
        val unitReference = typeLevelReference("Unit")
        val node = function(
            returnType = unitReference
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(unitReference to UnitMetaType)
        )

        val functionContext = typeContext.enterFunction(function(), handle = null, effect = EmptyEffect)
        typeCheckFunctionStatementAllPhases(node, functionContext)
        typeContext.undefer()
        assertThat(
            functionContext.typeOf(node),
            isFunctionType()
        )
        assertThat(
            typeContext.toTypes().functionType(node),
            isFunctionType()
        )
    }

    private fun typeCheckFunctionDefinition(node: FunctionDefinitionNode, context: TypeContext) {
        typeCheckModuleStatementAllPhases(node, context)
    }
}
