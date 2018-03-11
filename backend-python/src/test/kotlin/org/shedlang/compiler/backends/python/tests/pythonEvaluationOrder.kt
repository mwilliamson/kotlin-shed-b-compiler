package org.shedlang.compiler.backends.python.tests

import org.shedlang.compiler.backends.python.GeneratedCode
import org.shedlang.compiler.backends.python.ast.*


internal fun pythonEvaluationOrder(code: GeneratedCode<PythonExpressionNode>): List<PythonNode> {
    return code.statements.flatMap(::pythonNodeEvaluationOrder) + pythonNodeEvaluationOrder(code.value)
}

private fun pythonNodeEvaluationOrder(node: PythonNode): List<PythonNode> {
    return listOf(node) + when (node) {
        is PythonStatementNode -> statementChildrenEvaluationOrder(node)
        is PythonExpressionNode -> expressionChildrenEvaluationOrder(node)
        else -> throw UnsupportedOperationException("not implemented")
    }.flatMap(::pythonNodeEvaluationOrder)
}

private fun statementChildrenEvaluationOrder(statement: PythonStatementNode): List<PythonNode> {
    return statement.accept(object: PythonStatementNode.Visitor<List<PythonNode>> {
        override fun visit(node: PythonImportFromNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonClassNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonFunctionNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonExpressionStatementNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonReturnNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonIfStatementNode): List<PythonNode> {
            return node.conditionalBranches.flatMap { branch ->
                listOf(branch.condition) + branch.body
            } + node.elseBranch
        }

        override fun visit(node: PythonPassNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonAssignmentNode): List<PythonNode> {
            return listOf(node.expression, node.target)
        }
    })
}

private fun expressionChildrenEvaluationOrder(expression: PythonExpressionNode): List<PythonNode> {
    return expression.accept(object: PythonExpressionNode.Visitor<List<PythonNode>> {
        override fun visit(node: PythonNoneLiteralNode) = listOf<PythonNode>()
        override fun visit(node: PythonBooleanLiteralNode) = listOf<PythonNode>()
        override fun visit(node: PythonIntegerLiteralNode) = listOf<PythonNode>()
        override fun visit(node: PythonStringLiteralNode) = listOf<PythonNode>()
        override fun visit(node: PythonVariableReferenceNode) = listOf<PythonNode>()

        override fun visit(node: PythonBinaryOperationNode): List<PythonNode> {
            return listOf(node.left, node.right)
        }

        override fun visit(node: PythonFunctionCallNode): List<PythonNode> {
            return listOf(node.function) + node.arguments + node.keywordArguments.map { (_, argument) -> argument }
        }

        override fun visit(node: PythonAttributeAccessNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PythonLambdaNode): List<PythonNode> {
            throw UnsupportedOperationException("not implemented")
        }
    })
}
