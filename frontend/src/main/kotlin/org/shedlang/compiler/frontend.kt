package org.shedlang.compiler

import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.nextId
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.*


fun read(filename: String, input: String): ModuleNode {
    val module = parse(filename = filename, input = input)
    val intTypeNodeId = nextId()
    val variableReferences = resolve(module, mapOf(
        "Int" to intTypeNodeId
    ))
    typeCheck(module, TypeContext(
        returnType = null,
        variables = mutableMapOf(
            intTypeNodeId to MetaType(IntType)
        ),
        variableReferences = variableReferences
    ))
    checkReturns(module)
    return module
}
