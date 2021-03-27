package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.*

internal class ClosureCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler
) {
    internal fun compileCreate(
        target: LlvmVariable,
        functionName: String,
        freeVariables: List<LocalLoad>,
        positionalParams: List<LlvmParameter>,
        namedParams: List<Pair<Identifier, LlvmParameter>>,
        compileBody: (FunctionContext) -> FunctionContext,
        context: FunctionContext,
    ): FunctionContext {
        val closureEnvironmentParameter = LlvmParameter(compiledClosureEnvironmentPointerType, irBuilder.generateName("environment"))
        val explicitParams = positionalParams + namedParams.sortedBy { (paramName, _) -> paramName }.map { (_, param) -> param }
        val params = listOf(closureEnvironmentParameter) + explicitParams

        val bodyContextWithEnvironment = loadFreeVariables(
            freeVariables = freeVariables,
            closureEnvironmentPointer = LlvmOperandLocal(closureEnvironmentParameter.name),
            context = FunctionContext.initial(generateName = irBuilder::generateName),
        )

        val bodyContext = compileBody(bodyContextWithEnvironment)

        val identifier = irBuilder.generateName(functionName)

        val functionDefinition = LlvmFunctionDefinition(
            name = identifier,
            returnType = compiledValueType,
            parameters = params,
            body = bodyContext.instructions
        )

        return context
            .addTopLevelEntities(bodyContext.topLevelEntities)
            .addTopLevelEntities(functionDefinition)
            .let {
                createClosure(
                    target = target,
                    functionName = identifier,
                    parameterTypes = explicitParams.map { param -> param.type },
                    freeVariables = freeVariables,
                    context = it
                )
            }
    }

    internal fun createClosure(
        target: LlvmVariable,
        functionName: String,
        parameterTypes: List<LlvmType>,
        freeVariables: List<LocalLoad>,
        context: FunctionContext
    ): FunctionContext {
        val closurePointer = LlvmOperandLocal(irBuilder.generateName("closurePointer"))
        val closurePointerType = compiledClosurePointerType(parameterTypes)
        val closureMalloc = libc.typedMalloc(closurePointer, compiledClosureSize(freeVariables.size), type = closurePointerType)

        val getClosureAddress = LlvmPtrToInt(
            target = target,
            targetType = compiledValueType,
            value = closurePointer,
            sourceType = closurePointerType
        )

        return context
            .addInstructions(closureMalloc)
            .let {
                storeClosure(
                    closurePointer = closurePointer,
                    functionName = functionName,
                    parameterTypes = parameterTypes,
                    freeVariables = freeVariables,
                    context = it
                )
            }
            .addInstructions(getClosureAddress)
    }

    internal fun storeClosure(
        closurePointer: LlvmOperand,
        functionName: String,
        parameterTypes: List<LlvmType>,
        freeVariables: List<LocalLoad>,
        context: FunctionContext
    ): FunctionContext {
        val closurePointerType = compiledClosurePointerType(parameterTypes)

        val closureFunctionPointer = LlvmOperandLocal(irBuilder.generateName("closureFunctionPointer"))
        val closureEnvironmentPointer = LlvmOperandLocal(irBuilder.generateName("closureEnvironmentPointer"))

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

        return context
            .addInstructions(getClosureFunctionPointer)
            .addInstructions(storeClosureFunction)
            .addInstructions(getClosureEnvironmentPointer)
            .let { storeFreeVariables(
                freeVariables = freeVariables,
                closureEnvironmentPointer = closureEnvironmentPointer,
                context = it
            ) }
    }

    internal fun callClosure(
        target: LlvmOperandLocal,
        closurePointer: LlvmOperand,
        arguments: List<LlvmOperand>,
        tail: Boolean = false,
    ): List<LlvmInstruction> {
        val typedClosurePointer = LlvmOperandLocal(irBuilder.generateName("closurePointer"))
        val functionPointerPointer = LlvmOperandLocal(irBuilder.generateName("functionPointerPointer"))
        val functionPointer = LlvmOperandLocal(irBuilder.generateName("functionPointer"))
        val environmentPointer = LlvmOperandLocal(irBuilder.generateName("environmentPointer"))

        val argumentTypes = arguments.map { compiledValueType }
        val compiledClosurePointerType = compiledClosurePointerType(argumentTypes)

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
                type = compiledClosureFunctionPointerType(arguments.map { compiledValueType }),
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
                arguments = listOf(LlvmTypedOperand(compiledClosureEnvironmentPointerType, environmentPointer)) +
                    arguments.map { argument -> LlvmTypedOperand(compiledValueType, argument) },
                tailMarker = if (tail) { LlvmTailMarker.MUST_TAIL } else { null }
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

    private fun loadFreeVariables(
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
        closurePointer: LlvmOperand
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
        closurePointer: LlvmOperand
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
