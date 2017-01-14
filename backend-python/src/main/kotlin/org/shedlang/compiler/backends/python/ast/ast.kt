package org.shedlang.compiler.backends.python.ast

interface PythonNode

data class PythonModuleNode(
    val statements: List<PythonStatementNode>
) : PythonNode

interface PythonStatementNode
