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

    val ioEffectNodeId = freshNodeId()

    val printNodeId = freshNodeId()
    val intToStringNodeId = freshNodeId()

    val variableReferences = resolve(module, mapOf(
        "Unit" to unitTypeNodeId,
        "Int" to intTypeNodeId,

        "!io" to ioEffectNodeId,

        "print" to printNodeId,
        "intToString" to intToStringNodeId
    ))

    val variables = mutableMapOf(
        unitTypeNodeId to MetaType(UnitType),
        intTypeNodeId to MetaType(IntType),

        ioEffectNodeId to EffectType(IoEffect),

        printNodeId to FunctionType(
            positionalArguments = listOf(StringType),
            namedArguments = mapOf(),
            effects = listOf(IoEffect),
            returns = UnitType
        ),
        intToStringNodeId to positionalFunctionType(listOf(IntType), StringType)
    )
    val typeContext = TypeContext(
        returnType = null,
        effects = listOf(),
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
