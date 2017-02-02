package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.allOf

inline internal fun <reified T: ExpressionNode> isReturn(
    expression: Matcher<T>
): Matcher<StatementNode> {
    return cast(has(ReturnNode::expression, cast(expression)))
}

inline internal fun <reified T: ExpressionNode> isExpressionStatement(
    expression: Matcher<T>
): Matcher<StatementNode> {
    return cast(has(ExpressionStatementNode::expression, cast(expression)))
}

internal fun isBinaryOperation(
    operator: Operator,
    left: Matcher<ExpressionNode>,
    right: Matcher<ExpressionNode>
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(BinaryOperationNode::operator, equalTo(operator)),
        has(BinaryOperationNode::left, left),
        has(BinaryOperationNode::right, right)
    ))
}

internal fun isFunctionCall(
    left: Matcher<ExpressionNode>,
    arguments: Matcher<List<ExpressionNode>>
) : Matcher<ExpressionNode> {
    return cast(allOf(
        has(FunctionCallNode::function, left),
        has(FunctionCallNode::arguments, arguments)
    ))
}

internal fun isVariableReference(name: String) = cast(has(VariableReferenceNode::name, equalTo(name)))
