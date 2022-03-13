package org.shedlang.compiler.stackir

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

fun removeUnreachableCode(instruction: Instruction): Instruction {
    return instruction.mapChildren(::removeUnreachableCode)
}

fun removeUnreachableCode(instructions: List<Instruction>): PersistentList<Instruction> {
    val newInstructions = mutableListOf<Instruction>()
    val labelPredecessors = mutableSetOf<Int>()
    var isTerminated = false

    for (originalInstruction in instructions) {
        val instruction = removeUnreachableCode(originalInstruction)
        if (instruction is Label && labelPredecessors.contains(instruction.value)) {
            isTerminated = false
        }

        if (!isTerminated) {
            newInstructions.add(instruction)

            val next = when (instruction) {
                is JumpEnd -> instruction.destinationLabel
                is JumpIfTrue -> instruction.destinationLabel
                is JumpIfFalse -> instruction.destinationLabel
                else -> null
            }
            if (next != null) {
                labelPredecessors.add(next)
            }

            when (instruction) {
                is Exit, is JumpEnd, Resume, ResumeWithState, Return ->
                    isTerminated = true
            }
        }
    }

    return newInstructions.toPersistentList()
}
