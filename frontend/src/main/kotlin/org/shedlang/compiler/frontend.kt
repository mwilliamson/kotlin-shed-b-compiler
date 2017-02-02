package org.shedlang.compiler

import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.nextId
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.*


fun read(filename: String, input: String): ModuleNode {
    val module = parse(filename = filename, input = input)
    val intTypeNodeId = nextId()
    val unitTypeNodeId = nextId()
    val printNodeId = nextId()
    val variableReferences = resolve(module, mapOf(
        "Unit" to unitTypeNodeId,
        "Int" to intTypeNodeId,
        "print" to printNodeId
    ))
    val variables = mutableMapOf(
        unitTypeNodeId to MetaType(UnitType),
        intTypeNodeId to MetaType(IntType),
        // TODO: should be String -> Unit
        printNodeId to FunctionType(listOf(IntType), UnitType)
    )
    val typeContext = TypeContext(
        returnType = null,
        variables = variables,
        variableReferences = variableReferences
    )
    typeCheck(module, typeContext)
    checkReturns(module, variables)
    return module
}
