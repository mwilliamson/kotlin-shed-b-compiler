package org.shedlang.compiler.backends.tests

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.readPackage
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.moduleType
import org.shedlang.compiler.types.AnyType
import org.shedlang.compiler.types.Type

internal fun callFunction(
    environment: StackIrExecutionEnvironment,
    moduleName: ModuleName,
    functionName: String,
    arguments: List<IrValue>,
    type: Type
): StackExecutionResult {
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

    return environment.executeInstructions(instructions, type = type, moduleSet = moduleSet)
}
