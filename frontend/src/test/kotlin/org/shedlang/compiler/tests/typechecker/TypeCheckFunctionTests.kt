package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.MissingReturnTypeError
import org.shedlang.compiler.typechecker.UnhandledEffectError
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.*

class TypeCheckFunctionTests {
    @Test
    fun typeParameterTypeIsAdded() {
        val unitReference = staticReference("Unit")

        val typeParameter = typeParameter("T")
        val typeParameterReference = staticReference("T")
        val node = function(
            staticParameters = listOf(typeParameter),
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
                has(FunctionType::staticParameters, isSequence(
                    cast(has(TypeParameter::name, equalTo("T")))
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
            arguments = listOf(argument(type = functionTypeNode(effects = listOf(effectParameterReference), returnType = unitReference))),
            returnType = unitReference
        )
        val typeContext = typeContext(
            references = mapOf(effectParameterReference to effectParameter),
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        )

        typeCheck(node, typeContext)
        typeContext.undefer()

        assertThat(typeContext.typeOf(effectParameter), isEffectType(
            cast(has(EffectParameter::name, equalTo("E")))
        ))
        assertThat(
            typeContext.typeOf(effectParameterReference),
            equalTo(typeContext.typeOf(effectParameter))
        )
        assertThat(
            typeContext.typeOf(node),
            cast(
                has(FunctionType::staticParameters, isSequence(
                    cast(has(EffectParameter::name, equalTo("E")))
                ))
            )
        )
    }

    @Test
    fun bodyOfFunctionIsTypeChecked() {
        val unit = staticReference("Unit")
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
    fun finalStatementInBodyMustReturnCorrectType() {
        val intType = staticReference("Int")
        val typeContext = typeContext(referenceTypes = mapOf(intType to MetaType(IntType)))
        val node = function(
            returnType = intType,
            body = listOf(expressionStatement(literalBool(true)))
        )
        typeCheck(node, typeContext)

        assertThat(
            { typeContext.undefer() },
            throwsUnexpectedType(expected = IntType, actual = BoolType)
        )
    }

    @Test
    fun functionArgumentsAreTyped() {
        val intType = staticReference("Int")
        val argument = argument(name = "x", type = intType)
        val argumentReference = variableReference("x")
        val node = function(
            arguments = listOf(argument),
            returnType = intType,
            body = listOf(expressionStatement(argumentReference))
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
        val intType = staticReference("Int")
        val boolType = staticReference("Bool")
        val node = function(
            arguments = listOf(
                argument(name = "x", type = intType),
                argument(name = "y", type = boolType)
            ),
            returnType = intType,
            body = listOf(expressionStatement(literalInt()))
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
        val unitType = staticReference("Unit")
        val effect = staticReference("!io")

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
            effect = cast(equalTo(IoEffect))
        ))
    }

    @Test
    fun effectsAreAddedToBodyContext() {
        val unitType = staticReference("Unit")
        val effect = staticReference("!io")
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
                effect = IoEffect,
                returns = UnitType
            ),
            unitType to MetaType(UnitType),
            effect to EffectType(IoEffect)
        ))
        typeCheck(node, typeContext)
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
                expressionStatement(call(functionReference))
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                unitType to MetaType(UnitType)
            )
        )
        assertThat(
            {
                typeCheck(node, typeContext)
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
                expressionStatement(call(functionReference))
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                unitType to MetaType(UnitType)
            ),
            effect = IoEffect
        )
        assertThat(
            {
                typeCheck(node, typeContext)
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
            body = call(functionReference)
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                functionReference to functionType(
                    effect = IoEffect,
                    returns = UnitType
                ),
                unitType to MetaType(UnitType)
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
            arguments = listOf(),
            returnType = intType,
            body = literalBool()
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(intType to MetaType(IntType))
        )
        assertThat(
            { inferType(node, typeContext) },
            throwsUnexpectedType(expected = isIntType, actual = isBoolType)
        )
    }

    @Test
    fun whenExplicitReturnTypeIsMissingThenReturnTypeIsTypeOfExpressionBody() {
        val node = functionExpression(
            arguments = listOf(),
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
            arguments = listOf(),
            returnType = anyReference,
            body = literalBool()
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(anyReference to MetaType(AnyType))
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
            body = listOf(expressionStatement(returnValue, source = source))
        )
        val functionReference = variableReference("f")
        val call = call(
            receiver = functionReference,
            positionalArguments = listOf(node)
        )
        val receiverType = functionType(
            positionalArguments = listOf(
                functionType(returns = IntType)
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(functionReference to receiverType)
        )
        assertThat(
            { inferType(call, typeContext); typeContext.undefer() },
            throwsUnexpectedType(expected = isIntType, actual = isBoolType, source = equalTo(source))
        )
    }
}
