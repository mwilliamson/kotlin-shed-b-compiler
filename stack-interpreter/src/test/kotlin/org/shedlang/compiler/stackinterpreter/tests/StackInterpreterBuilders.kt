package org.shedlang.compiler.stackinterpreter.tests

import org.shedlang.compiler.stackinterpreter.InterpreterValue
import org.shedlang.compiler.stackinterpreter.NullWorld
import org.shedlang.compiler.stackinterpreter.World
import org.shedlang.compiler.stackir.Image
import org.shedlang.compiler.stackir.Instruction

internal fun executeInstructions(
    instructions: List<Instruction>,
    image: Image = Image.EMPTY,
    variables: Map<Int, InterpreterValue> = mapOf(),
    world: World = NullWorld
): InterpreterValue {
    val finalState = org.shedlang.compiler.stackinterpreter.executeInstructions(
        instructions,
        image = image,
        defaultVariables = variables,
        world = world
    )
    return finalState.popTemporary().second
}
