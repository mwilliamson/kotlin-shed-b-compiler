package org.shedlang.compiler.backends.python.tests

import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.tests.anySource

fun pythonModule(body: List<PythonStatementNode>)
    = PythonModuleNode(body = body, source = anySource())

fun pythonImportFrom(module: String, names: List<String>)
    = PythonImportFromNode(module = module, names = names, source = anySource())

fun pythonClass(
    name: String,
    body: List<PythonStatementNode> = listOf()
) = PythonClassNode(
    name = name,
    body = body,
    source = anySource()
)

fun pythonFunction(
    name: String,
    arguments: List<String> = listOf(),
    body: List<PythonStatementNode> = listOf()
) = PythonFunctionNode(
    name = name,
    arguments = arguments,
    body = body,
    source = anySource()
)

fun pythonReturn(expression: PythonExpressionNode)
    = PythonReturnNode(expression, source = anySource())

fun pythonExpressionStatement(expression: PythonExpressionNode)
    = PythonExpressionStatementNode(expression, source = anySource())

fun pythonIf(
    condition: PythonExpressionNode,
    trueBranch: List<PythonStatementNode>,
    falseBranch: List<PythonStatementNode> = listOf()
) = PythonIfStatementNode(
    condition = condition,
    trueBranch = trueBranch,
    falseBranch = falseBranch,
    source = anySource()
)

fun pythonAssignment(target: PythonExpressionNode, expression: PythonExpressionNode)
    = PythonAssignmentNode(target = target, expression = expression, source = anySource())

fun pythonNone()
    = PythonNoneLiteralNode(source = anySource())

fun pythonLiteralBoolean(value: Boolean)
    = PythonBooleanLiteralNode(value, source = anySource())

fun pythonLiteralInt(value: Int)
    = PythonIntegerLiteralNode(value, source = anySource())

fun pythonLiteralString(value: String)
    = PythonStringLiteralNode(value, source = anySource())

fun pythonVariableReference(name: String)
    = PythonVariableReferenceNode(name, source = anySource())

fun pythonBinaryOperation(
    operator: PythonOperator,
    left: PythonExpressionNode,
    right: PythonExpressionNode
) = PythonBinaryOperationNode(
    operator = operator,
    left = left,
    right = right,
    source = anySource()
)

fun pythonFunctionCall(
    function: PythonExpressionNode,
    arguments: List<PythonExpressionNode>,
    keywordArguments: Map<String, PythonExpressionNode> = mapOf()
) = PythonFunctionCallNode(
    function = function,
    arguments = arguments,
    keywordArguments = keywordArguments,
    source = anySource()
)

fun pythonAttributeAccess(
    receiver: PythonExpressionNode,
    attributeName: String
) = PythonAttributeAccessNode(
    receiver = receiver,
    attributeName = attributeName,
    source = anySource()
)
