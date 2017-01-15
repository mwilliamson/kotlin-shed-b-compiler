package org.shedlang.compiler.backends.python.tests

import org.shedlang.compiler.backends.python.ast.PythonBooleanLiteralNode
import org.shedlang.compiler.backends.python.ast.PythonIntegerLiteralNode
import org.shedlang.compiler.tests.typechecker.anySourceLocation

fun pythonLiteralBoolean(value: Boolean)
    = PythonBooleanLiteralNode(value, source = anySourceLocation())

fun pythonLiteralInt(value: Int)
    = PythonIntegerLiteralNode(value, source = anySourceLocation())
