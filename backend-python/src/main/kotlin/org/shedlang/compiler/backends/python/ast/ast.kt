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

interface PythonExpressionNode : PythonNode

data class PythonBooleanLiteralNode(
    val value: Boolean,
    override val source: Source
): PythonExpressionNode

data class PythonIntegerLiteralNode(
    val value: Int,
    override val source: Source
): PythonExpressionNode
