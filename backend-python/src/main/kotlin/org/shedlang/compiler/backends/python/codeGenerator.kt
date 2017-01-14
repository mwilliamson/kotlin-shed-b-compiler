package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.NodeSource
import org.shedlang.compiler.backends.python.ast.PythonModuleNode
import org.shedlang.compiler.backends.python.ast.PythonNode

fun generateCode(node: ModuleNode): PythonNode {
    return PythonModuleNode(listOf(), source = NodeSource(node))
}
