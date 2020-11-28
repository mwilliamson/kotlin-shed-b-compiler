package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckFunctionTests {
    @Test
    fun typeParameterTypeIsAdded() {
        val unitReference = staticReference("Unit")

        val typeParameter = typeParameter("T")
        val typeParameterReference = staticReference("T")
        val parameter = parameter(type = typeParameterReference)
        val node = function(
            staticParameters = listOf(typeParameter),
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
                has(FunctionType::staticParameters, isSequence(
                    cast(has(TypeParameter::name, isIdentifier("T")))
                ))
            )
        )
    }

    @Test
    fun effectParameterTypeIsAdded() {
        val unitReference = staticReference("Unit")

        val effectParameter = effectParameterDeclaration("E")
        val effectParameterReference = staticReference("E")
        val node = function(
            staticParameters = listOf(effectParameter),
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
            equalTo((typeContext.typeOf(effectParameter) as StaticValueType).value)
        )
        assertThat(
            typeContext.typeOf(node),
            cast(
                has(FunctionType::staticParameters, isSequence(
                    cast(has(EffectParameter::name, isIdentifier("E")))
                ))
            )
        )
    }

    @Test
    fun bodyOfFunctionIsTypeChecked() {
        val unit = staticReference("Unit")
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
        val intType = staticReference("Int")
        val typeContext = typeContext(referenceTypes = mapOf(intType to IntMetaType))
        val node = function(
            returnType = intType,
            body = listOf(expressionStatementReturn(literalBool(true)))
        )
        typeCheckFunctionDefinition(node, typeContext)

        assertThat(
            { typeContext.undefer() },
            throwsUnexpectedType(expected = IntType, actual = BoolType)
        )
    }

    @Test
    fun positionalParametersAreTyped() {
        val intType = staticReference("Int")
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
    fun namedParametersAreTyped() {
        val intType = staticReference("Int")
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
    fun signatureOfFunctionIsDeterminedFromArgumentsAndReturnType() {
        val intType = staticReference("Int")
        val boolType = staticReference("Bool")
        val node = function(
            parameters = listOf(
                parameter(name = "x", type = intType),
                parameter(name = "y", type = boolType)
            ),
            returnType = intType,
            body = listOf(expressionStatement(literalInt()))
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
        val unitType = staticReference("Unit")
        val effect = staticReference("!Io")

        val node = function(
            returnType = unitType,
            effect = effect
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            unitType to UnitMetaType,
            effect to StaticValueType(IoEffect)
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
        val unitType = staticReference("Unit")
        val effect = staticReference("!Io")
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
            effect to StaticValueType(IoEffect)
        ))
        typeCheckFunctionDefinition(node, typeContext)
        // TODO: come up with a way of ensuring undefer() is eventually called
        typeContext.undefer()
    }

    @Test
    fun errorIsThrowIfBodyStatementsHaveUnspecifiedEffect() {
        val unitType = staticReference("Unit")
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
    fun effectsAreNotInheritedByNestedFunctionBodyStatements() {
        val unitType = staticReference("Unit")
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
        val unitType = staticReference("Unit")
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
        val intType = staticReference("Int")
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
        val anyReference = staticReference("Any")
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
    fun whenExplicitReturnTypeIsMissingAndReturnTypeCannotBeInferredThenErrorIsThrown() {
        val node = functionExpression(
            returnType = null,
            body = listOf(expressionStatement(literalBool()))
        )
        val typeContext = typeContext()
        assertThat(
            { inferType(node, typeContext) },
            throwsException(has(MissingReturnTypeError::message, equalTo("Could not infer return type for function")))
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
            throwsUnexpectedType(expected = cast(isIntType), actual = isBoolType, source = equalTo(source))
        )
    }

    @Test
    fun canTypeCheckFunctionDefinitionAsModuleStatement() {
        val unitReference = staticReference("Unit")
        val node = function(
            returnType = unitReference
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(unitReference to UnitMetaType)
        )

        typeCheckModuleStatement(node, typeContext)
        typeContext.undefer()
        assertThat(
            typeContext.typeOf(node),
            isFunctionType()
        )
    }

    @Test
    fun canTypeCheckFunctionDefinitionAsFunctionStatement() {
        val unitReference = staticReference("Unit")
        val node = function(
            returnType = unitReference
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(unitReference to UnitMetaType)
        )

        val functionContext = typeContext.enterFunction(function(), handle = null, effect = EmptyEffect)
        typeCheckFunctionStatement(node, functionContext)
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
}
