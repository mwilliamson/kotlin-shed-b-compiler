package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionExpressionNode
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isPair
import org.shedlang.compiler.tests.isSequence

class ParseHandleTests {
    @Test
    fun canParseHandleWithOneHandler() {
        val source = "handle Try { f() } on { .throw = (value: String) { 42 } }"

        val node = parseString(::parseExpression, source)

        assertThat(node, isHandle(
            effect = isStaticReference("Try"),
            body = isBlock(
                isExpressionStatement(expression = isCall(receiver = isVariableReference("f")))
            ),
            handlers = isSequence(
                isPair(isIdentifier("throw"), allOf(
                    has(FunctionExpressionNode::parameters, isSequence(isParameter("value", "String"))),
                    has(FunctionExpressionNode::body, isBlock(isExpressionStatement(isIntLiteral(42))))
                ))
            )
        ))
    }
}
