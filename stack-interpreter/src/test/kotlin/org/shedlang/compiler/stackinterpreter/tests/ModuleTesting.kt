package org.shedlang.compiler.stackinterpreter.tests

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.readPackage
import org.shedlang.compiler.stackinterpreter.InterpreterValue
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.moduleType
import org.shedlang.compiler.types.AnyType

internal fun callFunction(
    moduleName: ModuleName,
    functionName: String,
    arguments: List<IrValue>
): CallFunctionResult {
    val world = InMemoryWorld()

    val instructions = persistentListOf(
        ModuleInit(moduleName),
        ModuleLoad(moduleName),
        FieldAccess(Identifier(functionName), receiverType = AnyType)
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

    val value = executeInstructions(instructions, image = image, world = world)
    return CallFunctionResult(value = value, stdout = world.stdout)
}

internal data class CallFunctionResult(val value: InterpreterValue, val stdout: String)
