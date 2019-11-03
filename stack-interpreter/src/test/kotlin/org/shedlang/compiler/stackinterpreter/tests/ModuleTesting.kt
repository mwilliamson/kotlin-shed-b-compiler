package org.shedlang.compiler.stackinterpreter.tests

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.readPackage
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.tests.moduleType

internal fun callFunction(
    moduleName: List<Identifier>,
    functionName: String,
    arguments: List<InterpreterValue>,
    world: World = NullWorld
): InterpreterValue {
    val instructions = persistentListOf(
        InitModule(moduleName),
        LoadModule(moduleName),
        FieldAccess(Identifier(functionName))
    )
        .addAll(arguments.map { argument -> PushValue(argument) })
        .add(Call(positionalArgumentCount = arguments.size, namedArgumentNames = listOf()))

    val optionsModules = readPackage(
        base = findRoot().resolve("stdlib"),
        name = listOf(Identifier("Stdlib"), Identifier("Options"))
    ).modules

    val moduleSet = ModuleSet(optionsModules + listOf(
        Module.Native(name = moduleName, type = moduleType())
    ))
    val image = loadModuleSet(moduleSet)

    return executeInstructions(instructions, image = image, world = world)
}
