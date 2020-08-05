package org.shedlang.compiler.backends.tests

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.readPackageModule
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.Type

internal fun callFunction(
    environment: StackIrExecutionEnvironment,
    moduleName: ModuleName,
    functionName: String,
    arguments: List<IrValue>,
    type: Type
): StackExecutionResult {
    val moduleSet = readPackageModule(
        base = findRoot().resolve("stdlib"),
        name = moduleName
    )

    val moduleType = moduleSet.moduleType(moduleName)!!

    val instructions = persistentListOf(
        ModuleInit(moduleName),
        ModuleLoad(moduleName),
        FieldAccess(Identifier(functionName), receiverType = moduleType)
    )
        .addAll(arguments.map { argument -> PushValue(argument) })
        .add(Call(positionalArgumentCount = arguments.size, namedArgumentNames = listOf()))

    return environment.executeInstructions(instructions, type = type, moduleSet = moduleSet)
}
