package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionBody
import org.shedlang.compiler.ast.FunctionDeclarationNode
import org.shedlang.compiler.ast.FunctionNode
import org.shedlang.compiler.ast.StaticReferenceNode
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.parser.parseFunctionDeclaration
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isInvariant
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.typechecker.MissingReturnTypeError

class ParseFunctionTests {
    @Test
    fun canReadZeroArgumentFunctionSignature() {
        val source = "fun f() -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, allOf(
            has(FunctionDeclarationNode::name, isIdentifier("f")),
            has(FunctionNode::staticParameters, isSequence()),
            has(FunctionNode::parameters, isSequence()),
            has(FunctionNode::returnType, present(cast(
                has(StaticReferenceNode::name, isIdentifier("Unit"))
            )))
        ))
    }

    @Test
    fun canReadOneArgumentFunctionSignature() {
        val source = "fun f(x: Int) -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::parameters, isSequence(
            isParameter("x", "Int")
        )))
    }

    @Test
    fun canReadManyArgumentFunctionSignature() {
        val source = "fun f(x: Int, y: String) -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::parameters, isSequence(
            isParameter("x", "Int"),
            isParameter("y", "String")
        )))
    }

    @Test
    fun canReadNamedParametersWithNoPositionalArguments() {
        val source = "fun f(*, x: Int) -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, allOf(
            has(FunctionNode::parameters, isSequence()),
            has(FunctionNode::namedParameters, isSequence(isParameter("x", "Int")))
        ))
    }

    @Test
    fun canReadNamedParametersAfterPositionalArguments() {
        val source = "fun f(x: Int, *, y: Int) -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, allOf(
            has(FunctionNode::parameters, isSequence(isParameter("x", "Int"))),
            has(FunctionNode::namedParameters, isSequence(isParameter("y", "Int")))
        ))
    }

    @Test
    fun parametersCanHaveTrailingComma() {
        val source = "fun f(x: Int, ) -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::parameters, isSequence(
            isParameter("x", "Int")
        )))
    }

    @Test
    fun canReadTypeParameters() {
        val source = "fun f[T, U](t: T) -> U { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, allOf(
            has(FunctionNode::staticParameters, isSequence(
                isTypeParameter(name = isIdentifier("T"), variance = isInvariant),
                isTypeParameter(name = isIdentifier("U"), variance = isInvariant)
            ))
        ))
    }

    @Test
    fun typeParametersCannotHaveVariance() {
        val source = "fun f[+T]() -> T { }"
        assertThat(
            { parseString(::parseFunctionDeclaration, source) },
            throws<UnexpectedTokenException>()
        )
    }

    @Test
    fun canReadEffectParameters() {
        val source = "fun f[!E]() -> U { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, allOf(
            has(FunctionNode::staticParameters, isSequence(
                isEffectParameterNode(name = isIdentifier("E"))
            ))
        ))
    }

    @Test
    fun canReadBody() {
        val source = "fun f() -> Int { 1; 2; }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionDeclarationNode::bodyStatements, isSequence(
            isExpressionStatement(isIntLiteral(equalTo(1))),
            isExpressionStatement(isIntLiteral(equalTo(2)))
        )))
    }

    @Test
    fun canReadFunctionWithZeroEffects() {
        val source = "fun f() -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::effects, isSequence()))
    }

    @Test
    fun canReadFunctionWithOneEffect() {
        val source = "fun f() !Io -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::effects, isSequence(
            isStaticReference("Io")
        )))
    }

    @Test
    fun canReadFunctionWithMultipleEffects() {
        val source = "fun f() !A, !B -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::effects, isSequence(
            isStaticReference("A"),
            isStaticReference("B")
        )))
    }

    @Test
    fun errorIsRaisedWhenFunctionDeclarationDoesNotProvideReturnType() {
        val source = "fun f() { }"
        assertThat(
            { parseString(::parseFunctionDeclaration, source) },
            throws(has(MissingReturnTypeError::message, equalTo("Function declaration must have return type")))
        )
    }

    @Test
    fun canParseAnonymousFunctionExpression() {
        val source = "fun () -> Unit { }"
        val function = parseString(::parseExpression, source)
        assertThat(function, cast(allOf(
            has(FunctionNode::staticParameters, isSequence()),
            has(FunctionNode::parameters, isSequence()),
            has(FunctionNode::returnType, present(cast(
                has(StaticReferenceNode::name, isIdentifier("Unit"))
            )))
        )))
    }

    @Test
    fun bodyOfFunctionExpressionCanBeExpression() {
        val source = "fun () -> Int => 4"
        val function = parseString(::parseExpression, source)
        assertThat(function, cast(
            has(FunctionNode::body, cast(has(FunctionBody.Expression::expression, isIntLiteral(equalTo(4)))))
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
                cast(has(FunctionBody.Expression::expression, isIntLiteral(equalTo(4))))
            )
        )))
    }
}
