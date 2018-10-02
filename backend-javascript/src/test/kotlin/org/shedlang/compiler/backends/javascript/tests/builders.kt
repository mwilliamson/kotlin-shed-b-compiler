package org.shedlang.compiler.backends.javascript.tests

import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.tests.anySource

fun jsLiteralNull() = JavascriptNullLiteralNode(
    source = anySource()
)

fun jsLiteralBool(value: Boolean) = JavascriptBooleanLiteralNode(
    value = value,
    source = anySource()
)

fun jsLiteralInt(value: Int) = JavascriptIntegerLiteralNode(
    value = value.toBigInteger(),
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

fun jsUnaryOperation(
    operator: JavascriptUnaryOperator,
    operand: JavascriptExpressionNode
) = JavascriptUnaryOperationNode(
    operator = operator,
    operand = operand,
    source = anySource()
)

fun jsBinaryOperation(
    operator: JavascriptBinaryOperator,
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
    elseBranch: List<JavascriptStatementNode> = listOf()
) = jsIfStatement(
    conditionalBranches = listOf(
        jsConditionalBranch(condition = condition, body = trueBranch)
    ),
    elseBranch = elseBranch
)

fun jsIfStatement(
    conditionalBranches: List<JavascriptConditionalBranchNode>,
    elseBranch: List<JavascriptStatementNode> = listOf()
) = JavascriptIfStatementNode(
    conditionalBranches = conditionalBranches,
    elseBranch = elseBranch,
    source = anySource()
)

fun jsConditionalBranch(
    condition: JavascriptExpressionNode,
    body: List<JavascriptStatementNode>
) = JavascriptConditionalBranchNode(
    condition = condition,
    body = body,
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

fun jsArray(elements: List<JavascriptExpressionNode>) = JavascriptArrayLiteralNode(
    elements = elements,
    source = anySource()
)

fun jsObject(
    properties: Map<String, JavascriptExpressionNode>
) = JavascriptObjectLiteralNode(
    properties = properties,
    source = anySource()
)

fun jsFunction(
    name: String,
    parameters: List<String> = listOf(),
    body: List<JavascriptStatementNode> = listOf()
) = JavascriptFunctionDeclarationNode(
    name = name,
    parameters = parameters,
    body = body,
    source = anySource()
)

fun jsFunctionExpression(
    parameters: List<String> = listOf(),
    body: List<JavascriptStatementNode> = listOf()
) = JavascriptFunctionExpressionNode(
    parameters = parameters,
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
