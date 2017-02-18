package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.backends.javascript.generateCode
import org.shedlang.compiler.tests.*

class CodeGeneratorTests {
    @Test
    fun emptyModuleGeneratesEmptyModule() {
        val shed = module(body = listOf())

        val node = generateCode(shed)

        assertThat(node, isJavascriptModule(equalTo(listOf())))
    }

    @Test
    fun moduleGeneratesModule() {
        val shed = module(body = listOf(function(name = "f")))

        val node = generateCode(shed)

        assertThat(node, isJavascriptModule(
            body = isSequence(isJavascriptFunction(name = equalTo("f")))
        ))
    }

    @Test
    fun functionGeneratesFunction() {
        val shed = function(
            name = "f",
            arguments = listOf(argument("x"), argument("y")),
            body = listOf(returns(literalInt(42)))
        )

        val node = generateCode(shed)

        assertThat(node.single(), isJavascriptFunction(
            name = equalTo("f"),
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isSequence(isJavascriptReturn(isJavascriptIntegerLiteral(42)))
        ))
    }

    @Test
    fun expressionStatementGeneratesExpressionStatement() {
        val shed = expressionStatement(literalInt(42))

        val node = generateCode(shed)

        assertThat(node, cast(has(
            JavascriptExpressionStatementNode::expression,
            isJavascriptIntegerLiteral(42)
        )))
    }

    @Test
    fun returnStatementGeneratesReturnStatement() {
        val shed = returns(literalInt(42))

        val node = generateCode(shed)

        assertThat(node, cast(has(
            JavascriptReturnNode::expression,
            isJavascriptIntegerLiteral(42)
        )))
    }

    @Test
    fun ifStatementGeneratesIfStatement() {
        val shed = ifStatement(
            literalInt(42),
            listOf(returns(literalInt(0))),
            listOf(returns(literalInt(1)))
        )

        val node = generateCode(shed)

        assertThat(node, cast(allOf(
            has(JavascriptIfStatementNode::condition, isJavascriptIntegerLiteral(42)),
            has(JavascriptIfStatementNode::trueBranch, isSequence(
                isJavascriptReturn(isJavascriptIntegerLiteral(0))
            )),
            has(JavascriptIfStatementNode::falseBranch, isSequence(
                isJavascriptReturn(isJavascriptIntegerLiteral(1))
            ))
        )))
    }

    @Test
    fun valGeneratesConst() {
        val shed = valStatement(name = "x", expression = literalBool(true))

        val node = generateCode(shed)

        assertThat(node, cast(allOf(
            has(JavascriptConstNode::name, equalTo("x")),
            has(JavascriptConstNode::expression, isJavascriptBooleanLiteral(true))
        )))
    }

    @Test
    fun unitLiteralGeneratesNull() {
        val shed = literalUnit()
        val node = generateCode(shed)
        assertThat(node, isJavascriptNull())
    }

    @Test
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, isJavascriptBooleanLiteral(true))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, isJavascriptIntegerLiteral(42))
    }

    @Test
    fun stringLiteralGeneratesStringLiteral() {
        val shed = literalString("<string>")
        val node = generateCode(shed)
        assertThat(node, isJavascriptStringLiteral("<string>"))
    }

    @Test
    fun variableReferenceGenerateVariableReference() {
        val shed = variableReference("x")

        val node = generateCode(shed)

        assertThat(node, isJavascriptVariableReference("x"))
    }

    @TestFactory
    fun binaryOperationGeneratesBinaryOperation(): List<DynamicTest> {
        return listOf(
            Operator.ADD to JavascriptOperator.ADD,
            Operator.SUBTRACT to JavascriptOperator.SUBTRACT,
            Operator.MULTIPLY to JavascriptOperator.MULTIPLY,
            Operator.EQUALS to JavascriptOperator.EQUALS
        ).map({ operator ->  DynamicTest.dynamicTest(
            operator.first.toString(), {
                val shed = binaryOperation(
                    operator = operator.first,
                    left = literalInt(0),
                    right = literalInt(1)
                )

                val node = generateCode(shed)

                assertThat(node, isJavascriptBinaryOperation(
                    operator = equalTo(operator.second),
                    left = isJavascriptIntegerLiteral(0),
                    right = isJavascriptIntegerLiteral(1)
                ))
            })
        })
    }

    @Test
    fun isOperationGeneratesTypeCheck() {
        val reference = variableReference("x")
        val typeReference = typeReference("X")

        val shed = isOperation(
            expression = reference,
            type = typeReference
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptFunctionCall(
            // TODO: should be a field access
            isJavascriptVariableReference("\$shed.isType"),
            isSequence(isJavascriptVariableReference("x"), isJavascriptVariableReference("X"))
        ))
    }

    @Test
    fun functionCallGeneratesFunctionCall() {
        val shed = call(variableReference("f"), listOf(literalInt(42)))

        val node = generateCode(shed)

        assertThat(node, isJavascriptFunctionCall(
            isJavascriptVariableReference("f"),
            isSequence(isJavascriptIntegerLiteral(42))
        ))
    }

    @Test
    fun shapeCallGeneratesObject() {
        val shed = call(
            variableReference("X"),
            namedArguments = listOf(callNamedArgument("a", literalBool(true)))
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptObject(
            properties = isMap(
                "a" to isJavascriptBooleanLiteral(true)
            )
        ))
    }

    @Test
    fun fieldAccessGeneratesPropertyAccess() {
        val shed = fieldAccess(variableReference("x"), "y")

        val node = generateCode(shed)

        assertThat(node, isJavascriptPropertyAccess(
            isJavascriptVariableReference("x"),
            equalTo("y")
        ))
    }

    private fun isJavascriptModule(body: Matcher<List<JavascriptStatementNode>>)
        = cast(has(JavascriptModuleNode::body, body))

    private fun isJavascriptFunction(
        name: Matcher<String>,
        arguments: Matcher<List<String>> = anything,
        body: Matcher<List<JavascriptStatementNode>> = anything
    ) : Matcher<JavascriptStatementNode>
        = cast(allOf(
            has(JavascriptFunctionNode::name, name),
            has(JavascriptFunctionNode::arguments, arguments),
            has(JavascriptFunctionNode::body, body)
        ))

    private fun isJavascriptReturn(expression: Matcher<JavascriptExpressionNode>)
        : Matcher<JavascriptStatementNode>
        = cast(has(JavascriptReturnNode::expression, expression))

    private fun isJavascriptBooleanLiteral(value: Boolean)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptBooleanLiteralNode::value, equalTo(value)))

    private fun isJavascriptNull()
        : Matcher<JavascriptExpressionNode>
        = isA<JavascriptNullLiteralNode>()

    private fun isJavascriptIntegerLiteral(value: Int)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptIntegerLiteralNode::value, equalTo(value)))

    private fun isJavascriptStringLiteral(value: String)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptStringLiteralNode::value, equalTo(value)))

    private fun isJavascriptVariableReference(name: String)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptVariableReferenceNode::name, equalTo(name)))

    private fun isJavascriptBinaryOperation(
        operator: Matcher<JavascriptOperator>,
        left: Matcher<JavascriptExpressionNode>,
        right: Matcher<JavascriptExpressionNode>
    ) : Matcher<JavascriptExpressionNode>
    = cast(allOf(
        has(JavascriptBinaryOperationNode::operator, operator),
        has(JavascriptBinaryOperationNode::left, left),
        has(JavascriptBinaryOperationNode::right, right)
    ))

    private fun isJavascriptFunctionCall(
        function: Matcher<JavascriptExpressionNode>,
        arguments: Matcher<List<JavascriptExpressionNode>>
    ) : Matcher<JavascriptExpressionNode>
    = cast(allOf(
        has(JavascriptFunctionCallNode::function, function),
        has(JavascriptFunctionCallNode::arguments, arguments)
    ))

    private fun isJavascriptPropertyAccess(
        receiver: Matcher<JavascriptExpressionNode>,
        propertyName: Matcher<String>
    ) : Matcher<JavascriptExpressionNode> = cast(allOf(
        has(JavascriptPropertyAccessNode::receiver, receiver),
        has(JavascriptPropertyAccessNode::propertyName, propertyName)
    ))

    private fun isJavascriptObject(
        properties: Matcher<Map<String, JavascriptExpressionNode>>
    ): Matcher<JavascriptExpressionNode> = cast(
        has(JavascriptObjectLiteralNode::properties, properties)
    )
}
