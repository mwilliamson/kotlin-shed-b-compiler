package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName

internal interface LabelPredecessor {
    val stack: PersistentList<LlvmOperand>
    val basicBlockName: String
}

internal class FunctionContext(
    override val stack: PersistentList<LlvmOperand>,
    internal val locals: PersistentMap<Int, LlvmOperand>,
    private val onLocalStore: PersistentMultiMap<Int, (LlvmOperand, FunctionContext) -> FunctionContext>,
    internal val instructions: PersistentList<LlvmInstruction>,
    override val basicBlockName: String,
    internal val topLevelEntities: PersistentList<LlvmTopLevelEntity>,
    private val definedModules: PersistentSet<ModuleName>,
    private val labelPredecessors: PersistentMultiMap<String, LabelPredecessor>,
    private val generateName: (String) -> String
): LabelPredecessor {
    fun addInstructions(vararg newInstructions: LlvmInstruction): FunctionContext {
        return addInstructions(newInstructions.asList())
    }

    fun addInstructions(newInstructions: List<LlvmInstruction>): FunctionContext {
        return newInstructions.fold(this) { acc, newInstruction ->
            acc.addInstruction(newInstruction)
        }
    }

    fun addInstruction(newInstruction: LlvmInstruction): FunctionContext {
        val newLabelPredecessors = when (newInstruction) {
            is LlvmBrUnconditional ->
                labelPredecessors.add(newInstruction.label, this)
            is LlvmBr ->
                labelPredecessors
                    .add(newInstruction.ifTrue, this)
                    .add(newInstruction.ifFalse, this)
            is LlvmLabel -> {
                val previousInstruction = instructions.lastOrNull()
                if (previousInstruction == null || isTerminator(previousInstruction)) {
                    labelPredecessors
                } else {
                    labelPredecessors.add(newInstruction.name, this)
                }
            }
            else ->
                labelPredecessors
        }

        val newBasicBlockName = when (newInstruction) {
            is LlvmLabel ->
                newInstruction.name
            else ->
                basicBlockName
        }

        val (newStack, extraInstructions) = when (newInstruction) {
            is LlvmLabel ->
                mergeStacks(newLabelPredecessors[newInstruction.name])
            else ->
                Pair(stack, listOf())
        }

        return FunctionContext(
            stack = newStack,
            locals = locals,
            onLocalStore = onLocalStore,
            instructions = instructions.add(newInstruction).addAll(extraInstructions),
            basicBlockName = newBasicBlockName,
            topLevelEntities = topLevelEntities,
            definedModules = definedModules,
            labelPredecessors = newLabelPredecessors,
            generateName = generateName
        )
    }
    fun addTopLevelEntities(vararg newTopLevelEntities: LlvmTopLevelEntity): FunctionContext {
        return addTopLevelEntities(newTopLevelEntities.asList())
    }

    fun addTopLevelEntities(newTopLevelEntities: List<LlvmTopLevelEntity>): FunctionContext {
        return FunctionContext(
            stack = stack,
            locals = locals,
            onLocalStore = onLocalStore,
            instructions = instructions,
            basicBlockName = basicBlockName,
            topLevelEntities = topLevelEntities.addAll(newTopLevelEntities),
            definedModules = definedModules,
            labelPredecessors = labelPredecessors,
            generateName = generateName
        )
    }

    fun defineModule(moduleName: List<Identifier>, function: () -> List<LlvmTopLevelEntity>): FunctionContext {
        if (definedModules.contains(moduleName)) {
            return this
        } else {
            return FunctionContext(
                stack = stack,
                locals = locals,
                onLocalStore = onLocalStore,
                instructions = instructions,
                basicBlockName = basicBlockName,
                topLevelEntities = topLevelEntities,
                definedModules = definedModules.add(moduleName),
                labelPredecessors = labelPredecessors,
                generateName = generateName
            ).addTopLevelEntities(function())
        }
    }

    fun localLoad(variableId: Int): LlvmOperand {
        return locals.getValue(variableId)
    }

    fun localLoad(variableId: Int, onStore: (LlvmOperand, FunctionContext) -> FunctionContext): FunctionContext {
        val value = locals[variableId]
        if (value == null) {
            return FunctionContext(
                stack = stack,
                locals = locals,
                onLocalStore = onLocalStore.add(variableId, onStore),
                instructions = instructions,
                basicBlockName = basicBlockName,
                topLevelEntities = topLevelEntities,
                definedModules = definedModules,
                labelPredecessors = labelPredecessors,
                generateName = generateName
            )
        } else {
            return onStore(value, this)
        }
    }

    fun localStore(variableId: Int, operand: LlvmOperand): FunctionContext {
        return updateLocals(locals.put(variableId, operand))
            .let { onLocalStore[variableId].fold(it) { context, func ->
                func(operand, context)
            } }
    }

    private fun updateLocals(newLocals: PersistentMap<Int, LlvmOperand>): FunctionContext {
        return FunctionContext(
            stack = stack,
            locals = newLocals,
            onLocalStore = onLocalStore,
            instructions = instructions,
            basicBlockName = basicBlockName,
            topLevelEntities = topLevelEntities,
            definedModules = definedModules,
            labelPredecessors = labelPredecessors,
            generateName = generateName
        )
    }

    fun pushTemporary(operand: LlvmOperand): FunctionContext {
        return updateStack(stack.add(operand))
    }

    fun popTemporary(): Pair<FunctionContext, LlvmOperand> {
        val (newStack, value) = stack.pop()
        return Pair(updateStack(newStack), value)
    }

    fun popTemporaries(count: Int): Pair<FunctionContext, List<LlvmOperand>> {
        val (newContext, operands) = (0 until count).fold(Pair(this, persistentListOf<LlvmOperand>())) { (newContext, operands), _ ->
            val (context2, operand) = newContext.popTemporary()
            Pair(context2, operands.add(operand))
        }

        return Pair(newContext, operands.reversed())
    }

    fun duplicateTemporary(): FunctionContext {
        return pushTemporary(peekTemporary())
    }

    fun discardTemporary(): FunctionContext {
        return updateStack(stack.removeAt(stack.lastIndex))
    }

    private fun peekTemporary() = stack.last()

    private fun updateStack(newStack: PersistentList<LlvmOperand>): FunctionContext {
        return FunctionContext(
            stack = newStack,
            locals = locals,
            onLocalStore = onLocalStore,
            instructions = instructions,
            basicBlockName = basicBlockName,
            topLevelEntities = topLevelEntities,
            definedModules = definedModules,
            labelPredecessors = labelPredecessors,
            generateName = generateName
        )
    }

    private fun mergeStacks(predecessors: List<LabelPredecessor>): Pair<PersistentList<LlvmOperand>, List<LlvmInstruction>> {
        val stacks = predecessors.map { predecessor -> predecessor.stack }
        val stackSizes = stacks.distinctBy { stack -> stack.size }
        if (stackSizes.size == 0) {
            return Pair(persistentListOf(), listOf())
        } else if (stackSizes.size == 1) {
            val (newStack, mergeInstructions) = (0 until stackSizes.single().size).map { stackIndex ->
                mergeOperands(predecessors, stackIndex)
            }.unzip()
            return Pair(newStack.toPersistentList(), mergeInstructions.flatten())
        } else {
            throw Exception("cannot merge stacks")
        }
    }

    private fun mergeOperands(predecessors: List<LabelPredecessor>, stackIndex: Int): Pair<LlvmOperand, List<LlvmInstruction>> {
        val distinctOperands = predecessors.map { predecessor -> predecessor.stack[stackIndex] }.distinct()
        if (distinctOperands.size == 1) {
            return Pair(distinctOperands.single(), listOf())
        } else {
            val mergedValue = LlvmOperandLocal(generateName("val"))
            val mergeInstruction = LlvmPhi(
                target = mergedValue,
                type = compiledValueType,
                pairs = predecessors.map { predecessor ->
                    LlvmPhiPair(value = predecessor.stack[stackIndex], predecessorBasicBlockName = predecessor.basicBlockName)
                }
            )
            return Pair(mergedValue, listOf(mergeInstruction))
        }
    }
}

private fun <T> PersistentList<T>.pop() = Pair(removeAt(lastIndex), last())
