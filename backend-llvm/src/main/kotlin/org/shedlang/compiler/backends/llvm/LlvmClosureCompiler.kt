package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.stackir.*

internal class ClosureCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler
) {
    internal fun createClosure(
        target: LlvmOperandLocal,
        functionName: String,
        parameterTypes: List<LlvmType>,
        freeVariables: List<LocalLoad>,
        context: FunctionContext
    ): PersistentList<LlvmInstruction> {
        val closurePointer = LlvmOperandLocal(irBuilder.generateName("closurePointer"))
        val closureFunctionPointer = LlvmOperandLocal(irBuilder.generateName("closureFunctionPointer"))
        val closureEnvironmentPointer = LlvmOperandLocal(irBuilder.generateName("closureEnvironmentPointer"))
        val closurePointerType = compiledClosurePointerType(parameterTypes)

        val closureMalloc = libc.typedMalloc(closurePointer, compiledClosureSize(freeVariables.size), type = closurePointerType)

        val getClosureFunctionPointer = LlvmGetElementPtr(
            target = closureFunctionPointer,
            pointerType = closurePointerType,
            pointer = closurePointer,
            indices = listOf(
                LlvmIndex.i64(0),
                LlvmIndex.i32(0)
            )
        )

        val storeClosureFunction = LlvmStore(
            type = compiledClosureFunctionPointerType(parameterTypes),
            value = LlvmOperandGlobal(functionName),
            pointer = closureFunctionPointer
        )

        val getClosureEnvironmentPointer = LlvmGetElementPtr(
            target = closureEnvironmentPointer,
            pointerType = closurePointerType,
            pointer = closurePointer,
            indices = listOf(
                LlvmIndex.i64(0),
                LlvmIndex.i32(1)
            )
        )

        val storeCapturedVariables = storeFreeVariables(
            freeVariables = freeVariables,
            closureEnvironmentPointer = closureEnvironmentPointer,
            context = context
        )

        val getClosureAddress = LlvmPtrToInt(
            target = target,
            targetType = compiledValueType,
            value = closurePointer,
            sourceType = closurePointerType
        )

        return persistentListOf<LlvmInstruction>()
            .addAll(closureMalloc)
            .add(getClosureFunctionPointer)
            .add(storeClosureFunction)
            .add(getClosureEnvironmentPointer)
            .addAll(storeCapturedVariables)
            .add(getClosureAddress)
    }

    internal fun findFreeVariables(instruction: Instruction): List<LocalLoad> {
        val descendants = instruction.descendantsAndSelf()
        val stores = descendants.filterIsInstance<LocalStore>()
        val storeIds = stores.map { store -> store.variableId }
        val parameterIds = descendants.filterIsInstance<DeclareFunction>().flatMap { function ->
            val parameters = function.positionalParameters + function.namedParameters
            parameters.map { parameter -> parameter.variableId }
        }
        val localIds = (storeIds + parameterIds).toSet()
        val loads = descendants.filterIsInstance<LocalLoad>().distinctBy { load -> load.variableId }
        return loads.filter { load -> !localIds.contains(load.variableId) }
    }

    internal fun callClosure(target: LlvmOperandLocal, closurePointer: LlvmOperand, arguments: List<LlvmTypedOperand>): List<LlvmInstruction> {
        val typedClosurePointer = LlvmOperandLocal(irBuilder.generateName("closurePointer"))
        val functionPointerPointer = LlvmOperandLocal(irBuilder.generateName("functionPointerPointer"))
        val functionPointer = LlvmOperandLocal(irBuilder.generateName("functionPointer"))
        val environmentPointer = LlvmOperandLocal(irBuilder.generateName("environmentPointer"))

        val compiledClosurePointerType = compiledClosurePointerType(arguments.map { argument -> argument.type })

        return listOf(
            LlvmIntToPtr(
                target = typedClosurePointer,
                sourceType = compiledValueType,
                value = closurePointer,
                targetType = compiledClosurePointerType
            ),
            LlvmGetElementPtr(
                target = functionPointerPointer,
                pointerType = compiledClosurePointerType,
                pointer = typedClosurePointer,
                indices = listOf(
                    LlvmIndex.i64(0),
                    LlvmIndex.i32(0)
                )
            ),
            LlvmLoad(
                target = functionPointer,
                type = compiledClosureFunctionPointerType(arguments.map { argument -> argument.type }),
                pointer = functionPointerPointer
            ),
            LlvmGetElementPtr(
                target = environmentPointer,
                pointerType = compiledClosurePointerType,
                pointer = typedClosurePointer,
                indices = listOf(
                    LlvmIndex.i64(0),
                    LlvmIndex.i32(1)
                )
            ),
            LlvmCall(
                target = target,
                returnType = compiledValueType,
                functionPointer = functionPointer,
                arguments = listOf(LlvmTypedOperand(compiledClosureEnvironmentPointerType, environmentPointer)) + arguments
            )
        )
    }

    private fun storeFreeVariables(
        freeVariables: List<LocalLoad>,
        closureEnvironmentPointer: LlvmOperandLocal,
        context: FunctionContext
    ): List<LlvmInstruction> {
        return freeVariables.mapIndexed { freeVariableIndex, freeVariable ->
            val capturedVariablePointer = LlvmOperandLocal(irBuilder.generateName(freeVariable.name.value + "Pointer"))

            listOf(
                capturedVariablePointer(
                    target = capturedVariablePointer,
                    closureEnvironmentPointer = closureEnvironmentPointer,
                    freeVariableIndex = freeVariableIndex
                ),
                LlvmStore(
                    type = compiledValueType,
                    value = context.localLoad(freeVariable.variableId),
                    pointer = capturedVariablePointer
                )
            )
        }.flatten()
    }

    internal fun loadFreeVariables(
        freeVariables: List<LocalLoad>,
        closureEnvironmentPointer: LlvmOperandLocal,
        context: FunctionContext
    ): FunctionContext {
        return freeVariables.foldIndexed(context) { freeVariableIndex, bodyContext, freeVariable ->
            val pointer = LlvmOperandLocal(irBuilder.generateName(freeVariable.name.value + "Pointer"))
            val value = LlvmOperandLocal(irBuilder.generateName(freeVariable.name))
            bodyContext.addInstructions(
                capturedVariablePointer(
                    target = pointer,
                    closureEnvironmentPointer = closureEnvironmentPointer,
                    freeVariableIndex = freeVariableIndex
                ),
                LlvmLoad(
                    target = value,
                    type = compiledValueType,
                    pointer = pointer
                )
            ).localStore(freeVariable.variableId, value)
        }
    }

    private fun capturedVariablePointer(
        target: LlvmOperandLocal,
        closureEnvironmentPointer: LlvmOperandLocal,
        freeVariableIndex: Int
    ): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledClosureEnvironmentPointerType,
            pointer = closureEnvironmentPointer,
            indices = listOf(
                LlvmIndex.i64(0),
                LlvmIndex.i64(freeVariableIndex)
            )
        )
    }
}
