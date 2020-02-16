package org.shedlang.compiler.backends.llvm

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
    ): FunctionContext {
        val closurePointer = LlvmOperandLocal(irBuilder.generateName("closurePointer"))
        val closureFunctionPointer = LlvmOperandLocal(irBuilder.generateName("closureFunctionPointer"))
        val closureEnvironmentPointer = LlvmOperandLocal(irBuilder.generateName("closureEnvironmentPointer"))
        val closurePointerType = compiledClosurePointerType(parameterTypes)

        val closureMalloc = libc.typedMalloc(closurePointer, compiledClosureSize(freeVariables.size), type = closurePointerType)

        val getClosureFunctionPointer = closureFunctionPointer(
            target = closureFunctionPointer,
            closurePointerType = closurePointerType,
            closurePointer = closurePointer
        )

        val storeClosureFunction = LlvmStore(
            type = compiledClosureFunctionPointerType(parameterTypes),
            value = LlvmOperandGlobal(functionName),
            pointer = closureFunctionPointer
        )

        val getClosureEnvironmentPointer = closureEnvironmentPointer(
            target = closureEnvironmentPointer,
            closurePointerType = closurePointerType,
            closurePointer = closurePointer
        )

        val getClosureAddress = LlvmPtrToInt(
            target = target,
            targetType = compiledValueType,
            value = closurePointer,
            sourceType = closurePointerType
        )

        return context
            .addInstructions(closureMalloc)
            .addInstructions(getClosureFunctionPointer)
            .addInstructions(storeClosureFunction)
            .addInstructions(getClosureEnvironmentPointer)
            .let { storeFreeVariables(
                freeVariables = freeVariables,
                closureEnvironmentPointer = closureEnvironmentPointer,
                context = it
            ) }
            .addInstructions(getClosureAddress)
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
            closureFunctionPointer(
                target = functionPointerPointer,
                closurePointerType = compiledClosurePointerType,
                closurePointer = typedClosurePointer
            ),
            LlvmLoad(
                target = functionPointer,
                type = compiledClosureFunctionPointerType(arguments.map { argument -> argument.type }),
                pointer = functionPointerPointer
            ),
            closureEnvironmentPointer(
                target = environmentPointer,
                closurePointerType = compiledClosurePointerType,
                closurePointer = typedClosurePointer
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
    ): FunctionContext {
        return freeVariables.foldIndexed(context) { freeVariableIndex, context, freeVariable ->
            storeFreeVariable(
                closureEnvironmentPointer = closureEnvironmentPointer,
                freeVariable = freeVariable,
                freeVariableIndex = freeVariableIndex,
                context = context
            )
        }
    }

    private fun storeFreeVariable(
        closureEnvironmentPointer: LlvmOperandLocal,
        freeVariable: LocalLoad,
        freeVariableIndex: Int,
        context: FunctionContext
    ): FunctionContext {
        return context.localLoad(freeVariable.variableId) { freeVariableValue, context ->
            val capturedVariablePointer = LlvmOperandLocal(irBuilder.generateName(freeVariable.name.value + "Pointer"))

            context.addInstructions(
                capturedVariablePointer(
                    target = capturedVariablePointer,
                    closureEnvironmentPointer = closureEnvironmentPointer,
                    freeVariableIndex = freeVariableIndex
                ),
                LlvmStore(
                    type = compiledValueType,
                    value = freeVariableValue,
                    pointer = capturedVariablePointer
                )
            )
        }
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

    private fun closureFunctionPointer(
        target: LlvmOperandLocal,
        closurePointerType: LlvmTypePointer,
        closurePointer: LlvmOperandLocal
    ): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = closurePointerType,
            pointer = closurePointer,
            indices = listOf(
                LlvmIndex.i64(0),
                LlvmIndex.i32(0)
            )
        )
    }

    private fun closureEnvironmentPointer(
        target: LlvmOperandLocal,
        closurePointerType: LlvmTypePointer,
        closurePointer: LlvmOperandLocal
    ): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = closurePointerType,
            pointer = closurePointer,
            indices = listOf(
                LlvmIndex.i64(0),
                LlvmIndex.i32(1)
            )
        )
    }
}
