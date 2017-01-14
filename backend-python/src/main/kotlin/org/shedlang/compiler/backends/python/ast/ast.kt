package org.shedlang.compiler.backends.python.ast

import org.shedlang.compiler.ast.Source

interface PythonNode {
    val source: Source
}

data class PythonModuleNode(
    val statements: List<PythonStatementNode>,
    override val source: Source
) : PythonNode

interface PythonStatementNode : PythonNode

data class PythonExpressionStatementNode(
    val expression: PythonExpressionNode,
    override val source: Source
) : PythonStatementNode

data class PythonReturnNode(
    val expression: PythonExpressionNode,
    override val source: Source
) : PythonStatementNode

data class PythonIfStatementNode(
    val condition: PythonExpressionNode,
    val trueBranch: List<PythonStatementNode>,
    val falseBranch: List<PythonStatementNode>,
    override val source: Source
) : PythonStatementNode

interface PythonExpressionNode : PythonNode

data class PythonBooleanLiteralNode(
    val value: Boolean,
    override val source: Source
): PythonExpressionNode

data class PythonIntegerLiteralNode(
    val value: Int,
    override val source: Source
): PythonExpressionNode

data class PythonVariableReferenceNode(
    val name: String,
    override val source: Source
): PythonExpressionNode

data class PythonBinaryOperationNode(
    val operator: PythonOperator,
    val left: PythonExpressionNode,
    val right: PythonExpressionNode,
    override val source: Source
): PythonExpressionNode

enum class PythonOperator {
    EQUALS,
    ADD,
    SUBTRACT,
    MULTIPLY
}

data class PythonFunctionCallNode(
    val function: PythonExpressionNode,
    val arguments: List<PythonExpressionNode>,
    override val source: Source
): PythonExpressionNode
