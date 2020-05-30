package org.shedlang.compiler.backends.python.tests

import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.tests.anySource

fun pythonModule(body: List<PythonStatementNode>)
    = PythonModuleNode(body = body, source = anySource())

fun pythonImportFrom(module: String, names: List<Pair<String, String>>)
    = PythonImportFromNode(module = module, names = names, source = anySource())

fun pythonClass(
    name: String,
    body: List<PythonStatementNode> = listOf(),
    baseClasses: List<PythonExpressionNode> = listOf()
) = PythonClassNode(
    name = name,
    body = body,
    baseClasses = baseClasses,
    source = anySource()
)

fun pythonFunction(
    name: String,
    parameters: List<String> = listOf(),
    body: List<PythonStatementNode> = listOf(),
    decorators: List<PythonExpressionNode> = listOf()
) = PythonFunctionNode(
    name = name,
    parameters = parameters,
    body = body,
    decorators = decorators,
    source = anySource()
)

fun pythonReturn(expression: PythonExpressionNode)
    = PythonReturnNode(expression, source = anySource())

fun pythonExpressionStatement(expression: PythonExpressionNode)
    = PythonExpressionStatementNode(expression, source = anySource())

fun pythonIf(
    condition: PythonExpressionNode,
    trueBranch: List<PythonStatementNode>,
    elseBranch: List<PythonStatementNode> = listOf()
) = pythonIf(
    conditionalBranches = listOf(
        pythonConditionalBranch(condition = condition, body = trueBranch)
    ),
    elseBranch = elseBranch
)

fun pythonIf(
    conditionalBranches: List<PythonConditionalBranchNode>,
    elseBranch: List<PythonStatementNode> = listOf()
) = PythonIfStatementNode(
    conditionalBranches = conditionalBranches,
    elseBranch = elseBranch,
    source = anySource()
)

fun pythonConditionalBranch(
    condition: PythonExpressionNode,
    body: List<PythonStatementNode>
) = PythonConditionalBranchNode(
    condition = condition,
    body = body,
    source = anySource()
)

fun pythonAssignment(target: PythonExpressionNode, expression: PythonExpressionNode)
    = PythonAssignmentNode(target = target, expression = expression, source = anySource())

fun pythonWhile(condition: PythonExpressionNode, body: List<PythonStatementNode>)
    = PythonWhileNode(condition = condition, body = body, source = anySource())

fun pythonNone()
    = PythonNoneLiteralNode(source = anySource())

fun pythonLiteralBoolean(value: Boolean)
    = PythonBooleanLiteralNode(value, source = anySource())

fun pythonLiteralInt(value: Int)
    = PythonIntegerLiteralNode(value.toBigInteger(), source = anySource())

fun pythonLiteralString(value: String)
    = PythonStringLiteralNode(value, source = anySource())

fun pythonVariableReference(name: String)
    = PythonVariableReferenceNode(name, source = anySource())

fun pythonTuple(vararg members: PythonExpressionNode)
    = PythonTupleNode(members.toList(), source = anySource())

fun pythonUnaryOperation(
    operator: PythonUnaryOperator,
    operand: PythonExpressionNode
) = PythonUnaryOperationNode(
    operator = operator,
    operand = operand,
    source = anySource()
)

fun pythonBinaryOperation(
    operator: PythonBinaryOperator,
    left: PythonExpressionNode,
    right: PythonExpressionNode
) = PythonBinaryOperationNode(
    operator = operator,
    left = left,
    right = right,
    source = anySource()
)

fun pythonConditionalOperation(
    condition: PythonExpressionNode,
    trueExpression: PythonExpressionNode,
    falseExpression: PythonExpressionNode
) = PythonConditionalOperationNode(
    condition = condition,
    trueExpression = trueExpression,
    falseExpression = falseExpression,
    source = anySource()
)

fun pythonFunctionCall(
    function: PythonExpressionNode,
    arguments: List<PythonExpressionNode>,
    keywordArguments: List<Pair<String, PythonExpressionNode>> = listOf()
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

fun pythonLambda(
    parameters: List<String>,
    body: PythonExpressionNode
) = PythonLambdaNode(
    parameters = parameters,
    body = body,
    source = anySource()
)
