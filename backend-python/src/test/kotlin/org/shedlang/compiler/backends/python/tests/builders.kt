package org.shedlang.compiler.backends.python.tests

import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.tests.typechecker.anySource

fun pythonReturn(expression: PythonExpressionNode)
    = PythonReturnNode(expression, source = anySource())

fun pythonExpressionStatement(expression: PythonExpressionNode)
    = PythonExpressionStatementNode(expression, source = anySource())

fun pythonLiteralBoolean(value: Boolean)
    = PythonBooleanLiteralNode(value, source = anySource())

fun pythonLiteralInt(value: Int)
    = PythonIntegerLiteralNode(value, source = anySource())

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
    arguments: List<PythonExpressionNode>
) = PythonFunctionCallNode(
    function = function,
    arguments = arguments,
    source = anySource()
)
