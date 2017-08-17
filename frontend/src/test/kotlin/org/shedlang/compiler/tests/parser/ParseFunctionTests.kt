package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.parser.parseFunctionDeclaration
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.isInvariant
import org.shedlang.compiler.tests.isSequence

class ParseFunctionTests {
    @Test
    fun canReadZeroArgumentFunctionSignature() {
        val source = "fun f() -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, allOf(
            has(FunctionDeclarationNode::name, equalTo("f")),
            has(FunctionNode::typeParameters, isSequence()),
            has(FunctionNode::arguments, isSequence()),
            has(FunctionNode::returnType, present(cast(
                has(TypeReferenceNode::name, equalTo("Unit"))
            )))
        ))
    }

    @Test
    fun canReadOneArgumentFunctionSignature() {
        val source = "fun f(x: Int) -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::arguments, isSequence(
            isArgument("x", "Int")
        )))
    }

    @Test
    fun canReadManyArgumentFunctionSignature() {
        val source = "fun f(x: Int, y: String) -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::arguments, isSequence(
            isArgument("x", "Int"),
            isArgument("y", "String")
        )))
    }

    @Test
    fun canReadTypeParameters() {
        val source = "fun f[T, U](t: T) -> U { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, allOf(
            has(FunctionNode::typeParameters, isSequence(
                isTypeParameter(name = equalTo("T"), variance = isInvariant),
                isTypeParameter(name = equalTo("U"), variance = isInvariant)
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
    fun canReadBody() {
        val source = "fun f() -> Int { return 1; return 2; }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionDeclarationNode::bodyStatements, isSequence(
            cast(has(ReturnNode::expression, cast(has(IntegerLiteralNode::value, equalTo(1))))),
            cast(has(ReturnNode::expression, cast(has(IntegerLiteralNode::value, equalTo(2)))))
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
        val source = "fun f() !io -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::effects, isSequence(
            isVariableReference("!io")
        )))
    }

    @Test
    fun canReadFunctionWithMultipleEffects() {
        val source = "fun f() !a, !b -> Unit { }"
        val function = parseString(::parseFunctionDeclaration, source)
        assertThat(function, has(FunctionNode::effects, isSequence(
            isVariableReference("!a"),
            isVariableReference("!b")
        )))
    }

    @Test
    fun canParseAnonymousFunctionExpression() {
        val source = "fun () -> Unit { }"
        val function = parseString(::parseExpression, source)
        assertThat(function, cast(allOf(
            has(FunctionNode::typeParameters, isSequence()),
            has(FunctionNode::arguments, isSequence()),
            has(FunctionNode::returnType, present(cast(
                has(TypeReferenceNode::name, equalTo("Unit"))
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
    fun whenBodyOfFunctionExpressionIsExpressionThenReturnTypeCanBeOmitted() {
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

    private fun isArgument(name: String, typeReference: String): Matcher<ArgumentNode> {
        return allOf(
            has(ArgumentNode::name, equalTo(name)),
            has(ArgumentNode::type, cast(
                has(TypeReferenceNode::name, equalTo(typeReference))
            ))
        )
    }
}
