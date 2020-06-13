package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.ast.FunctionExpressionNode
import org.shedlang.compiler.ast.HandlerNode
import org.shedlang.compiler.parser.ParseError
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence

class ParseHandleTests {
    @Test
    fun canParseHandleWithOneHandlerThatExits() {
        val source = "handle Try { f() } on { .throw = (value: String) { exit 42 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandle(
            effect = isStaticReference("Try"),
            body = isBlock(
                isExpressionStatement(expression = isCall(receiver = isVariableReference("f")))
            ),
            handlers = isSequence(
                isHandler(
                    operationName = isIdentifier("throw"),
                    function = allOf(
                        has(FunctionExpressionNode::parameters, isSequence(isParameter("value", "String"))),
                        has(FunctionExpressionNode::body, isBlock(isExpressionStatement(isIntLiteral(42))))
                    ),
                    type = equalTo(HandlerNode.Type.EXIT)
                )
            )
        ))
    }

    @Test
    fun canParseHandlerWithStatementsBeforeExiting() {
        val source = "handle Try { f() } on { .throw = (value: String) { unit; exit 42 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandle(
            handlers = isSequence(
                isHandler(
                    operationName = isIdentifier("throw"),
                    function = allOf(
                        has(FunctionExpressionNode::body, isBlock(
                            isExpressionStatement(isUnitLiteral(), type = equalTo(ExpressionStatementNode.Type.NO_RETURN)),
                            isExpressionStatement(isIntLiteral(42), type = equalTo(ExpressionStatementNode.Type.RETURN))
                        ))
                    ),
                    type = equalTo(HandlerNode.Type.EXIT)
                )
            )
        ))
    }

    @Test
    fun returningExpressionStatementBeforeExitThrowsError() {
        val source = "handle Try { f() } on { .throw = (value: String) { unit exit 42 } }"

        assertThat({ parseString(::parseExpression, source) }, throws<ParseError>(
            has(ParseError::message, equalTo("cannot return from a handler without explicit exit"))
        ))
    }
}
