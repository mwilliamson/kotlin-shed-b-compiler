package org.shedlang.compiler.stackinterpreter.tests

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.backends.tests.StackExecutionResult
import org.shedlang.compiler.backends.tests.StackIrExecutionEnvironment
import org.shedlang.compiler.stackinterpreter.interpreterValueToIrValue
import org.shedlang.compiler.stackir.Instruction
import org.shedlang.compiler.stackir.loadModuleSet
import org.shedlang.compiler.types.Type

object StackInterpreterExecutionEnvironment : StackIrExecutionEnvironment {
    override fun executeInstructions(instructions: List<Instruction>, type: Type, moduleSet: ModuleSet): StackExecutionResult {
        val world = InMemoryWorld()
        val interpreterValue = executeInstructions(
            instructions,
            image = loadModuleSet(moduleSet),
            world = world
        )
        return StackExecutionResult(
            value = interpreterValueToIrValue(interpreterValue),
            stdout = world.stdout
        )
    }
}
