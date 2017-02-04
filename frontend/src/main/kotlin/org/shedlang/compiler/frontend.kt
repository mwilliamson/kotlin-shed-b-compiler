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
    val intToStringNodeId = nextId()

    val variableReferences = resolve(module, mapOf(
        "Unit" to unitTypeNodeId,
        "Int" to intTypeNodeId,
        "print" to printNodeId,
        "intToString" to intToStringNodeId
    ))
    val variables = mutableMapOf(
        unitTypeNodeId to MetaType(UnitType),
        intTypeNodeId to MetaType(IntType),
        printNodeId to FunctionType(listOf(StringType), UnitType),
        intToStringNodeId to FunctionType(listOf(IntType), StringType)
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
