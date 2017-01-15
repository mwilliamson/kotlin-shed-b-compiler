package org.shedlang.compiler.backends.python.tests

import org.shedlang.compiler.backends.python.ast.PythonBooleanLiteralNode
import org.shedlang.compiler.backends.python.ast.PythonIntegerLiteralNode
import org.shedlang.compiler.backends.python.ast.PythonVariableReferenceNode
import org.shedlang.compiler.tests.typechecker.anySource

fun pythonLiteralBoolean(value: Boolean)
    = PythonBooleanLiteralNode(value, source = anySource())

fun pythonLiteralInt(value: Int)
    = PythonIntegerLiteralNode(value, source = anySource())

fun pythonVariableReference(name: String)
    = PythonVariableReferenceNode(name, source = anySource())
