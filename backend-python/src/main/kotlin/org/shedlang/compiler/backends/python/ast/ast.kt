package org.shedlang.compiler.backends.python.ast

import org.shedlang.compiler.ast.Source

interface PythonNode {
    val source: Source
}

data class PythonModuleNode(
    val statements: List<PythonStatementNode>,
    override val source: Source
) : PythonNode

interface PythonStatementNode
