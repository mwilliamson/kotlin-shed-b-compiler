package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.frontend.parser.*
import org.shedlang.compiler.tests.throwsException
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isInvariant
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.typechecker.MissingReturnTypeError

class ParseFunctionTests {
    @Test
    fun canReadZeroArgumentFunctionSignature() {
        val source = "fun f() -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, allOf(
            has(FunctionDefinitionNode::name, isIdentifier("f")),
            has(FunctionNode::typeLevelParameters, isSequence()),
            has(FunctionNode::parameters, isSequence()),
            has(FunctionNode::returnType, present(isTypeLevelReferenceNode("Unit")))
        ))
    }

    @Test
    fun canReadOneArgumentFunctionSignature() {
        val source = "fun f(x: Int) -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, has(FunctionNode::parameters, isSequence(
            isParameterNode("x", "Int")
        )))
    }

    @Test
    fun canReadManyArgumentFunctionSignature() {
        val source = "fun f(x: Int, y: String) -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, has(FunctionNode::parameters, isSequence(
            isParameterNode("x", "Int"),
            isParameterNode("y", "String")
        )))
    }

    @Test
    fun canReadNamedParametersWithNoPositionalArguments() {
        val source = "fun f(.x: Int) -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, allOf(
            has(FunctionNode::parameters, isSequence()),
            has(FunctionNode::namedParameters, isSequence(isParameterNode("x", "Int")))
        ))
    }

    @Test
    fun canReadNamedParametersAfterPositionalArguments() {
        val source = "fun f(x: Int, .y: Int) -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, allOf(
            has(FunctionNode::parameters, isSequence(isParameterNode("x", "Int"))),
            has(FunctionNode::namedParameters, isSequence(isParameterNode("y", "Int")))
        ))
    }

    @Test
    fun whenPositionalParameterFollowsNamedParameterThenErrorIsThrown() {
        val source = "fun f(.x: Int, y: Int) -> Unit { }"

        assertThat(
            { parseString(::parseFunctionDefinition, source) },
            throwsException<PositionalParameterAfterNamedParameterError>()
        )
    }

    @Test
    fun argumentTypeIsOptional() {
        val source = "fun f(x, .y) -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, isFunctionDefinitionNode(
            positionalParameters = isSequence(
                isParameterNode("x", absent()),
            ),
            namedParameters = isSequence(
                isParameterNode("y", absent()),
            ),
        ))
    }

    @Test
    fun parametersCanHaveTrailingComma() {
        val source = "fun f(x: Int, ) -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, has(FunctionNode::parameters, isSequence(
            isParameterNode("x", "Int")
        )))
    }

    @Test
    fun canReadTypeParameters() {
        val source = "fun f[T, U](t: T) -> U { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, allOf(
            has(FunctionNode::typeLevelParameters, isSequence(
                isTypeParameterNode(name = isIdentifier("T"), variance = isInvariant),
                isTypeParameterNode(name = isIdentifier("U"), variance = isInvariant)
            ))
        ))
    }

    @Test
    fun typeParametersCannotHaveVariance() {
        val source = "fun f[+T]() -> T { }"
        assertThat(
            { parseString(::parseFunctionDefinition, source) },
            throws<UnexpectedTokenError>()
        )
    }

    @Test
    fun canReadEffectParameters() {
        val source = "fun f[!E]() -> U { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, allOf(
            has(FunctionNode::typeLevelParameters, isSequence(
                isEffectParameterNode(name = isIdentifier("E"))
            ))
        ))
    }

    @Test
    fun canReadBody() {
        val source = "fun f() -> Int { 1; 2; }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, has(FunctionDefinitionNode::body, isBlockNode(
            isExpressionStatementNode(isIntLiteralNode(equalTo(1))),
            isExpressionStatementNode(isIntLiteralNode(equalTo(2)))
        )))
    }

    @Test
    fun canReadFunctionWithoutEffect() {
        val source = "fun f() -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, has(FunctionNode::effect, absent()))
    }

    @Test
    fun canReadFunctionWithEffect() {
        val source = "fun f() !Io -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, has(FunctionNode::effect, present(cast(
            has(FunctionEffectNode.Explicit::expression, isTypeLevelReferenceNode("Io")),
        ))))
    }

    @Test
    fun canReadFunctionWithInferredEffect() {
        val source = "fun f() !_ -> Unit { }"
        val function = parseString(::parseFunctionDefinition, source)
        assertThat(function, has(FunctionNode::effect, present(isA<FunctionEffectNode.Infer>())))
    }

    @Test
    fun errorIsRaisedWhenFunctionDefinitionDoesNotProvideReturnType() {
        val source = "fun f() { }"
        assertThat(
            { parseString(::parseFunctionDefinition, source) },
            throws(has(MissingReturnTypeError::message, equalTo("Function declaration must have return type")))
        )
    }

    @Test
    fun canParseAnonymousFunctionExpression() {
        val source = "fun () -> Unit { }"
        val function = parseString(::parseExpression, source)
        assertThat(function, cast(allOf(
            has(FunctionNode::typeLevelParameters, isSequence()),
            has(FunctionNode::parameters, isSequence()),
            has(FunctionNode::returnType, present(isTypeLevelReferenceNode("Unit")))
        )))
    }

    @Test
    fun bodyOfFunctionExpressionCanBeExpression() {
        val source = "fun () -> Int => 4"
        val function = parseString(::parseExpression, source)
        assertThat(function, cast(
            has(
                FunctionNode::body,
                isBlockNode(
                    isExpressionStatementNode(
                        expression = isIntLiteralNode(equalTo(4)),
                        type = equalTo(ExpressionStatementNode.Type.VALUE)
                    )
                )
            )
        ))
    }

    @Test
    fun returnTypeofFunctionExpressionCanBeOmitted() {
        val source = "fun () => 4"
        val function = parseString(::parseExpression, source)
        assertThat(function, cast(allOf(
            has(FunctionNode::returnType, absent()),
            has(
                FunctionNode::body,
                isBlockNode(
                    isExpressionStatementNode(
                        expression = isIntLiteralNode(equalTo(4)),
                        type = equalTo(ExpressionStatementNode.Type.VALUE)
                    )
                )
            )
        )))
    }

    @Test
    fun canReadFunctionDefinitionAsModuleStatement() {
        val source = "fun f() -> Unit { }"
        val function = parseString(::parseModuleStatement, source)
        assertThat(function, cast(allOf(
            has(FunctionDefinitionNode::name, isIdentifier("f"))
        )))
    }

    @Test
    fun canReadFunctionDefinitionAsFunctionStatement() {
        val source = "fun f() -> Unit { }"
        val function = parseString(::parseFunctionStatement, source)
        assertThat(function, cast(allOf(
            has(FunctionDefinitionNode::name, isIdentifier("f"))
        )))
    }

    @Test
    fun canReadFunctionExpressionAsFunctionExpressionStatement() {
        val source = "fun () -> Unit { }"
        val function = parseString(::parseFunctionStatement, source)
        assertThat(function, isExpressionStatementNode(expression = cast(allOf(
            has(FunctionExpressionNode::returnType, present(isTypeLevelReferenceNode("Unit")))
        ))))
    }
}
