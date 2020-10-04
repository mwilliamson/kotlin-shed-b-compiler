package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.ast.FunctionExpressionNode
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.literalInt

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
                        has(FunctionExpressionNode::body, isBlock(
                            isExit(isIntLiteral(42))
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

        assertThat(node, isHandle(
            handlers = isSequence(
                isHandler(
                    operationName = isIdentifier("throw"),
                    function = allOf(
                        has(FunctionExpressionNode::body, isBlock(
                            isExpressionStatement(isUnitLiteral(), type = equalTo(ExpressionStatementNode.Type.NO_VALUE)),
                            isExit(isIntLiteral(42))
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

        assertThat(node, isHandle(
            handlers = isSequence(
                isHandler(
                    operationName = isIdentifier("throw"),
                    function = allOf(
                        has(FunctionExpressionNode::parameters, isSequence(isParameter("value", "String"))),
                        has(FunctionExpressionNode::body, isBlock(
                            isResume(isIntLiteral(42))
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

        assertThat(node, isHandle(
            handlers = isSequence(
                isHandler(
                    operationName = isIdentifier("a")
                ),
                isHandler(
                    operationName = isIdentifier("b")
                )
            )
        ))
    }

    @Test
    fun canParseHandlerWithoutState() {
        val source = "handle Try { } on { .throw = () { exit unit } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandle(
            initialState = absent(),
        ))
    }

    @Test
    fun canParseHandlerWithState() {
        val source = "handle Try withstate(42) { } on { .throw = () { exit unit } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandle(
            initialState = present(isIntLiteral(42)),
        ))
    }

    @Test
    fun canParseResumeWithState() {
        val source = "handle Try withstate(42) { } on { .x = (state: Int) { resume unit withstate 43 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandle(
            handlers = isSequence(
                isHandler(
                    function = allOf(
                        has(FunctionExpressionNode::body, isBlock(
                            isResume(
                                expression = isUnitLiteral(),
                                newState = present(isIntLiteral(43)),
                            )
                        ))
                    )
                ),
            ),
        ))
    }
}
