package org.shedlang.compiler.backends.python.ast

import org.shedlang.compiler.ast.Source

interface PythonNode {
    val source: Source
}

data class PythonModuleNode(
    val body: List<PythonStatementNode>,
    override val source: Source
) : PythonNode

interface PythonStatementNode : PythonNode

data class PythonFunctionNode(
    val name: String,
    val arguments: List<String>,
    val body: List<PythonStatementNode>,
    override val source: Source
): PythonStatementNode

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

interface PythonExpressionNode : PythonNode {
    interface Visitor<T> {
        fun visit(node: PythonBooleanLiteralNode): T
        fun visit(node: PythonIntegerLiteralNode): T
        fun visit(node: PythonVariableReferenceNode): T
        fun visit(node: PythonBinaryOperationNode): T
        fun visit(node: PythonFunctionCallNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class PythonBooleanLiteralNode(
    val value: Boolean,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonIntegerLiteralNode(
    val value: Int,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonVariableReferenceNode(
    val name: String,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonBinaryOperationNode(
    val operator: PythonOperator,
    val left: PythonExpressionNode,
    val right: PythonExpressionNode,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

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
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}
