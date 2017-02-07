package org.shedlang.compiler

import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.*


class FrontEndResult(
    val module: ModuleNode,
    val references: VariableReferences
)


fun read(filename: String, input: String): FrontEndResult {
    val module = parse(filename = filename, input = input)

    val intTypeNodeId = freshNodeId()
    val unitTypeNodeId = freshNodeId()
    val printNodeId = freshNodeId()
    val intToStringNodeId = freshNodeId()

    val variableReferences = resolve(module, mapOf(
        "Unit" to unitTypeNodeId,
        "Int" to intTypeNodeId,
        "print" to printNodeId,
        "intToString" to intToStringNodeId
    ))
    val variables = mutableMapOf(
        unitTypeNodeId to MetaType(UnitType),
        intTypeNodeId to MetaType(IntType),
        printNodeId to positionalFunctionType(listOf(StringType), UnitType),
        intToStringNodeId to positionalFunctionType(listOf(IntType), StringType)
    )
    val typeContext = TypeContext(
        returnType = null,
        variables = variables,
        variableReferences = variableReferences
    )
    typeCheck(module, typeContext)
    checkReturns(module, variables)
    return FrontEndResult(
        module = module,
        references = variableReferences
    )
}
