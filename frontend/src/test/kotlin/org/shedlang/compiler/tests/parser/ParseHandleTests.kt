package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.ast.FunctionExpressionNode
import org.shedlang.compiler.frontend.parser.parseExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence

class ParseHandleTests {
    @Test
    fun canParseHandleWithOneHandlerThatExits() {
        val source = "handle Try { f() } on { .throw = (value: String) { exit 42 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandleNode(
            effect = isTypeLevelReferenceNode("Try"),
            body = isBlockNode(
                isExpressionStatementNode(expression = isCallNode(receiver = isVariableReferenceNode("f")))
            ),
            handlers = isSequence(
                isHandlerNode(
                    operationName = isIdentifier("throw"),
                    function = allOf(
                        has(FunctionExpressionNode::parameters, isSequence(isParameterNode("value", "String"))),
                        has(FunctionExpressionNode::body, isBlockNode(
                            isExitNode(isIntLiteralNode(42))
                        ))
                    )
                )
            )
        ))
    }

    @Test
    fun canParseHandlerWithStatementsBeforeExiting() {
        val source = "handle Try { f() } on { .throw = (value: String) { unit; exit 42 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandleNode(
            handlers = isSequence(
                isHandlerNode(
                    operationName = isIdentifier("throw"),
                    function = allOf(
                        has(FunctionExpressionNode::body, isBlockNode(
                            isExpressionStatementNode(isUnitLiteralNode(), type = equalTo(ExpressionStatementNode.Type.NO_VALUE)),
                            isExitNode(isIntLiteralNode(42))
                        ))
                    )
                )
            )
        ))
    }

    @Test
    fun canParseHandleWithOneHandlerThatResumes() {
        val source = "handle Try { f() } on { .throw = (value: String) { resume 42 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandleNode(
            handlers = isSequence(
                isHandlerNode(
                    operationName = isIdentifier("throw"),
                    function = allOf(
                        has(FunctionExpressionNode::parameters, isSequence(isParameterNode("value", "String"))),
                        has(FunctionExpressionNode::body, isBlockNode(
                            isResumeNode(isIntLiteralNode(42))
                        ))
                    )
                )
            )
        ))
    }

    @Test
    fun operationHandlersAreOrderedByName() {
        val source = "handle Eff { } on { .b = () { exit unit }, .a = () { exit unit } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandleNode(
            handlers = isSequence(
                isHandlerNode(
                    operationName = isIdentifier("a")
                ),
                isHandlerNode(
                    operationName = isIdentifier("b")
                )
            )
        ))
    }

    @Test
    fun canParseHandlerWithoutState() {
        val source = "handle Try { } on { .throw = () { exit unit } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandleNode(
            initialState = absent(),
        ))
    }

    @Test
    fun canParseHandlerWithState() {
        val source = "handle Try withstate(42) { } on { .throw = () { exit unit } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandleNode(
            initialState = present(isIntLiteralNode(42)),
        ))
    }

    @Test
    fun canParseResumeWithState() {
        val source = "handle Try withstate(42) { } on { .x = (state: Int) { resume unit withstate 43 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandleNode(
            handlers = isSequence(
                isHandlerNode(
                    function = allOf(
                        has(FunctionExpressionNode::body, isBlockNode(
                            isResumeNode(
                                expression = isUnitLiteralNode(),
                                newState = present(isIntLiteralNode(43)),
                            )
                        ))
                    )
                ),
            ),
        ))
    }
}
