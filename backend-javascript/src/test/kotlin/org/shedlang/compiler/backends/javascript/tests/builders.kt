package org.shedlang.compiler.backends.javascript.tests

import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.tests.anySource

fun jsLiteralBool(value: Boolean) = JavascriptBooleanLiteralNode(
    value = value,
    source = anySource()
)

fun jsLiteralInt(value: Int) = JavascriptIntegerLiteralNode(
    value = value,
    source = anySource()
)

fun jsLiteralString(value: String) = JavascriptStringLiteralNode(
    value = value,
    source = anySource()
)

fun jsVariableReference(name: String) = JavascriptVariableReferenceNode(
    name = name,
    source = anySource()
)

fun jsBinaryOperation(
    operator: JavascriptOperator,
    left: JavascriptExpressionNode,
    right: JavascriptExpressionNode
) = JavascriptBinaryOperationNode(
    operator = operator,
    left = left,
    right = right,
    source = anySource()
)

fun jsIfStatement(
    condition: JavascriptExpressionNode,
    trueBranch: List<JavascriptStatementNode>,
    falseBranch: List<JavascriptStatementNode> = listOf()
) = JavascriptIfStatementNode(
    condition = condition,
    trueBranch = trueBranch,
    falseBranch = falseBranch,
    source = anySource()
)

fun jsExpressionStatement(expression: JavascriptExpressionNode) = JavascriptExpressionStatementNode(
    expression = expression,
    source = anySource()
)

fun jsReturn(expression: JavascriptExpressionNode) = JavascriptReturnNode(
    expression = expression,
    source = anySource()
)

fun jsFunctionCall(
    function: JavascriptExpressionNode,
    arguments: List<JavascriptExpressionNode>
) = JavascriptFunctionCallNode(
    function = function,
    arguments = arguments,
    source = anySource()
)

fun jsPropertyAccess(
    receiver: JavascriptExpressionNode,
    propertyName: String
) = JavascriptPropertyAccessNode(
    receiver = receiver,
    propertyName = propertyName,
    source = anySource()
)

fun jsFunction(
    name: String,
    arguments: List<String> = listOf(),
    body: List<JavascriptStatementNode> = listOf()
) = JavascriptFunctionNode(
    name = name,
    arguments = arguments,
    body = body,
    source = anySource()
)

fun jsConst(
    name: String,
    expression: JavascriptExpressionNode
) = JavascriptConstNode(
    name = name,
    expression = expression,
    source = anySource()
)

fun jsModule(
    body: List<JavascriptStatementNode>
) = JavascriptModuleNode(
    body = body,
    source = anySource()
)
