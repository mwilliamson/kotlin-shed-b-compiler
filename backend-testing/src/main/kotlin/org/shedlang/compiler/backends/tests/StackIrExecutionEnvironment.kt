package org.shedlang.compiler.backends.tests

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.stackir.Instruction
import org.shedlang.compiler.stackir.IrValue
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.TypeRegistry

interface StackIrExecutionEnvironment {
    fun executeInstructions(
        instructions: List<Instruction>,
        type: Type,
        moduleSet: ModuleSet = ModuleSet(listOf(), typeRegistry = TypeRegistry.Empty)
    ): StackExecutionResult
}

data class StackExecutionResult(val value: IrValue, val stdout: String)
