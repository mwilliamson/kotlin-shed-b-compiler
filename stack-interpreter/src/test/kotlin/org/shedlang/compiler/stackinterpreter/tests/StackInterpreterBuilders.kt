package org.shedlang.compiler.stackinterpreter.tests

import kotlinx.collections.immutable.PersistentList
import org.shedlang.compiler.EMPTY_TYPES
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.Types
import org.shedlang.compiler.ast.Block
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.backends.tests.loader
import org.shedlang.compiler.stackinterpreter.InterpreterValue
import org.shedlang.compiler.stackinterpreter.NullWorld
import org.shedlang.compiler.stackinterpreter.World
import org.shedlang.compiler.stackir.Image
import org.shedlang.compiler.stackir.Instruction

internal fun evaluateBlock(block: Block, references: ResolvedReferences): InterpreterValue {
    val instructions = loader(references = references).loadBlock(block)
    return executeInstructions(instructions)
}

internal fun evaluateExpression(node: ExpressionNode, types: Types = EMPTY_TYPES): InterpreterValue {
    val instructions = loader(types = types).loadExpression(node)
    return executeInstructions(instructions)
}

internal fun executeInstructions(
    instructions: PersistentList<Instruction>,
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
