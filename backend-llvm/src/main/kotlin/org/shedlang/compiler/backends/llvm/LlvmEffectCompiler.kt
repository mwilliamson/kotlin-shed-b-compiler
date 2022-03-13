package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.types.UserDefinedEffect
import org.shedlang.compiler.types.effectType

internal class EffectCompiler(
    private val closures: ClosureCompiler,
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler,
    private val objects: LlvmObjectCompiler
) {
    private val effectIdType = LlvmTypes.i32
    private val effectHandlerType = CTypes.voidPointer
    private val operationHandlerType = CTypes.voidPointer
    private val operationHandlerContextType = CTypes.voidPointer
    private val operationIndexType = LlvmTypes.i32

    internal fun define(
        target: LlvmOperandLocal,
        effect: UserDefinedEffect,
        context: FunctionContext
    ): FunctionContext {
        val operationOperands = effect.operations.keys.associate { operationName ->
            operationName to irBuilder.generateLocal(operationName)
        }

        return context
            .let {
                effect.operations.entries.fold(it) { context2, (operationName, operationType) ->
                    val parameterCount = operationType.positionalParameters.size + operationType.namedParameters.size
                    val returnValue = irBuilder.generateLocal("return")
                    val llvmParameters = (0 until parameterCount).map { parameterIndex ->
                        LlvmParameter(type = compiledValueType, name = "arg" + parameterIndex)
                    }
                    val effectHandler = irBuilder.generateLocal("effectHandler")
                    val operationHandler = irBuilder.generateLocal("operationHandler")
                    val operationHandlerFunctionVoidPtr = irBuilder.generateLocal("operationHandlerFunctionVoidPtr")
                    val operationHandlerFunction = irBuilder.generateLocal("operationHandlerFunction")
                    val operationHandlerContext = irBuilder.generateLocal("operationHandlerContext")
                    // TODO: extract this? Presumably duplicated somewhere...
                    val operationIndex = effect.operations.keys.sorted().indexOf(operationName)

                    closures.compileCreate(
                        target = operationOperands.getValue(operationName),
                        functionName = operationName.value,
                        positionalParams = llvmParameters,
                        namedParams = listOf(),
                        freeVariables = listOf(),
                        compileBody = { bodyContext ->
                            bodyContext
                                .addInstructions(effectHandlersFindEffectHandler(
                                    target = effectHandler,
                                    effect = effect,
                                ))
                                .addInstructions(effectHandlersGetOperationHandler(
                                    target = operationHandler,
                                    effectHandler = effectHandler,
                                    operationIndex = operationIndex,
                                ))
                                .addInstructions(operationHandlerGetFunction(
                                    target = operationHandlerFunctionVoidPtr,
                                    operationHandler = operationHandler,
                                ))
                                .addInstructions(LlvmBitCast(
                                    target = operationHandlerFunction,
                                    sourceType = CTypes.voidPointer,
                                    value = operationHandlerFunctionVoidPtr,
                                    targetType = LlvmTypes.pointer(LlvmTypes.function(
                                        returnType = compiledValueType,
                                        // TODO: duplicated?
                                        parameterTypes = listOf(effectHandlerType, operationHandlerContextType) +
                                            llvmParameters.map { parameter -> parameter.type },
                                    )),
                                ))
                                .addInstructions(operationHandlerGetContext(
                                    target = operationHandlerContext,
                                    operationHandler = operationHandler,
                                ))
                                .addInstructions(LlvmCall(
                                    target = returnValue,
                                    functionPointer = operationHandlerFunction,
                                    returnType = compiledValueType,
                                    arguments = listOf(
                                        LlvmTypedOperand(effectHandlerType, effectHandler),
                                        LlvmTypedOperand(operationHandlerContextType, operationHandlerContext),
                                    ) + llvmParameters.map { llvmParameter ->
                                        LlvmTypedOperand(compiledValueType, LlvmOperandLocal(llvmParameter.name))
                                    }
                                ))
                                .addInstruction(LlvmReturn(type = compiledValueType, value = returnValue))
                        },
                        context = context2
                    )
                }
            }
            .addInstructions(objects.createObject(
                target = target,
                objectType = effectType(effect),
                fields = operationOperands.map { (operationName, operationOperand) ->
                    operationName to operationOperand
                }
            ))
    }

    internal fun handle(
        effect: UserDefinedEffect,
        compileBody: (FunctionContext) -> FunctionContext,
        operationHandlers: List<LlvmOperand>,
        initialState: LlvmOperand?,
        context: FunctionContext
    ): Pair<FunctionContext, LlvmOperand> {
        val setjmpResult = irBuilder.generateLocal("setjmpResult")
        val setjmpEnv = irBuilder.generateLocal("setjmpEnv")
        val normalLabel = irBuilder.generateName("normal")
        val untilLabel = irBuilder.generateName("until")
        val exitLabel = irBuilder.generateName("exit")
        val exitResult = irBuilder.generateLocal("exitResult")

        val context2 = context
            .addInstructions(libc.typedMalloc(
                target = setjmpEnv,
                bytes = CTypes.jmpBuf.byteSize,
                type = CTypes.jmpBufPointer
            ))
            .addInstructions(libc.setjmp(
                target = setjmpResult,
                env = setjmpEnv
            ))
            .addInstructions(LlvmSwitch(
                type = libc.setjmpReturnType,
                value = setjmpResult,
                defaultLabel = normalLabel,
                destinations = listOf(
                    0 to normalLabel,
                    1 to exitLabel
                )
            ))
            .addInstructions(LlvmLabel(normalLabel))
            .let {
                effectHandlersPush(
                    effect = effect,
                    operationHandlers = operationHandlers,
                    initialState = initialState,
                    setjmpEnv = setjmpEnv,
                    context = it
                )
            }
            .let { compileBody(it) }
            .addInstructions(effectHandlersDiscard())
            .addInstructions(LlvmBrUnconditional(untilLabel))
            .addInstructions(LlvmLabel(exitLabel))
            .addInstructions(loadOperationHandlerValue(target = exitResult))
            .pushTemporary(exitResult)
            .addInstructions(LlvmBrUnconditional(untilLabel))
            .addInstructions(LlvmLabel(untilLabel))

        return context2.popTemporary()
    }

    private val effectHandlersSetOperationHandlerDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_set_operation_handler",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler"),
            LlvmParameter(type = operationIndexType, name = "operation_index"),
            LlvmParameter(type = operationHandlerType, name = "function"),
            LlvmParameter(type = CTypes.voidPointer, name = "context")
        )
    )

    private fun effectHandlersSetOperationHandler(
        effectHandler: LlvmOperand,
        operationIndex: Int,
        operationHandlerFunction: LlvmOperand,
        operationHandlerContext: LlvmOperand
    ): LlvmInstruction {
        return effectHandlersSetOperationHandlerDeclaration.call(
            target = null,
            arguments = listOf(
                effectHandler,
                LlvmOperandInt(operationIndex),
                operationHandlerFunction,
                operationHandlerContext
            )
        )
    }

    private val effectHandlersPushDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_push",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = effectHandlerType,
        parameters = listOf(
            LlvmParameter(type = effectIdType, name = "effect_id"),
            LlvmParameter(type = CTypes.int, name = "operation_count"),
            LlvmParameter(type = CTypes.jmpBufPointer, name = "env"),
            LlvmParameter(type = compiledValueType, name = "initial_state"),
        )
    )

    private fun effectHandlersPush(
        effect: UserDefinedEffect,
        operationHandlers: List<LlvmOperand>,
        initialState: LlvmOperand?,
        setjmpEnv: LlvmOperand,
        context: FunctionContext
    ): FunctionContext {
        val effectHandler = irBuilder.generateLocal("effectHandler")

        val operationTypes = effect.operations.map { (_, operationType) -> operationType }

        return context
            .addInstructions(effectHandlersPushDeclaration.call(
                target = effectHandler,
                arguments = listOf(
                    LlvmOperandInt(effect.definitionId),
                    LlvmOperandInt(effect.operations.size),
                    setjmpEnv,
                    initialState ?: LlvmOperandInt(0),
                )
            ))
            .let {
                operationHandlers.foldIndexed(it) { operationIndex, context, operationHandler ->
                    val operationHandlerFunctionName = irBuilder.generateName("operationHandlerFunction")
                    val operationHandlerFunctionAsVoidPointer = irBuilder.generateLocal("operationHandlerFunctionAsVoidPointer")
                    val operationType = operationTypes[operationIndex]
                    val closure = irBuilder.generateLocal("operationHandlerClosure")
                    val previousEffectHandlerStack = irBuilder.generateLocal("previousEffectHandlerStack")

                    val parameterCount = operationType.positionalParameters.size + operationType.namedParameters.size
                    val llvmParameters = (0 until parameterCount).map { parameterIndex ->
                        LlvmParameter(type = compiledValueType, name = "arg" + parameterIndex)
                    }

                    val resumeValue = irBuilder.generateLocal("resumeValue")

                    context
                        .addTopLevelEntities(LlvmFunctionDefinition(
                            name = operationHandlerFunctionName,
                            returnType = compiledValueType,
                            parameters = listOf(
                                LlvmParameter(effectHandlerType, "effect_handler"),
                                LlvmParameter(compiledValueType, "context"),
                            ) + llvmParameters,
                            body = persistentListOf<LlvmInstruction>()
                                .add(enter(
                                    target = previousEffectHandlerStack,
                                    effectHandler = LlvmOperandLocal("effect_handler"),
                                ))
                                .addAll(callOperationHandler(
                                    target = resumeValue,
                                    effectHandler = LlvmOperandLocal("effect_handler"),
                                    operationHandler = LlvmOperandLocal("context"),
                                    hasState = initialState != null,
                                    arguments = llvmParameters.map { llvmParameter ->
                                        LlvmOperandLocal(llvmParameter.name)
                                    }
                                ))
                                .add(restore(previousEffectHandlerStack))
                                .add(LlvmReturn(compiledValueType, resumeValue))
                        ))
                        .addInstructions(LlvmBitCast(
                            target = operationHandlerFunctionAsVoidPointer,
                            targetType = CTypes.voidPointer,
                            value = LlvmOperandGlobal(operationHandlerFunctionName),
                            sourceType = LlvmTypes.pointer(LlvmTypes.function(
                                parameterTypes = listOf(
                                    effectHandlerType,
                                    compiledValueType,
                                ) + llvmParameters.map { llvmParameter -> llvmParameter.type },
                                returnType = compiledValueType
                            ))
                        ))
                        .addInstructions(LlvmIntToPtr(
                            target = closure,
                            targetType = CTypes.voidPointer,
                            value = operationHandler,
                            sourceType = compiledValueType
                        ))
                        .addInstructions(effectHandlersSetOperationHandler(
                            effectHandler = effectHandler,
                            operationIndex = operationIndex,
                            operationHandlerFunction = operationHandlerFunctionAsVoidPointer,
                            operationHandlerContext = closure
                        ))
                }
            }
    }

    private val effectHandlersDiscardDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_discard",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf()
    )

    private fun effectHandlersDiscard(): LlvmCall {
        return effectHandlersDiscardDeclaration.call(
            target = null,
            arguments = listOf()
        )
    }

    private val effectHandlersFindEffectHandlerDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_find_effect_handler",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = effectHandlerType,
        parameters = listOf(
            LlvmParameter(type = effectIdType, name = "effect_id"),
        )
    )

    private fun effectHandlersFindEffectHandler(
        target: LlvmOperandLocal,
        effect: UserDefinedEffect,
    ): List<LlvmInstruction> {
        val call = effectHandlersFindEffectHandlerDeclaration.call(
            target = target,
            arguments = listOf(
                LlvmOperandInt(effect.definitionId),
            )
        )
        return listOf(call)
    }

    private val effectHandlersOperationHandlerGetFunctionDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_operation_handler_get_function",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.voidPointer,
        parameters = listOf(
            LlvmParameter(type = operationHandlerType, name = "operation_handler"),
        )
    )

    private fun operationHandlerGetFunction(
        target: LlvmOperandLocal,
        operationHandler: LlvmOperand,
    ): List<LlvmInstruction> {
        val call = effectHandlersOperationHandlerGetFunctionDeclaration.call(
            target = target,
            arguments = listOf(operationHandler),
        )
        return listOf(call)
    }

    private val effectHandlersOperationHandlerGetContextDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_operation_handler_get_context",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.voidPointer,
        parameters = listOf(
            LlvmParameter(type = operationHandlerType, name = "operation_handler"),
        )
    )

    private fun operationHandlerGetContext(
        target: LlvmOperandLocal,
        operationHandler: LlvmOperand,
    ): List<LlvmInstruction> {
        val call = effectHandlersOperationHandlerGetContextDeclaration.call(
            target = target,
            arguments = listOf(operationHandler)
        )
        return listOf(call)
    }

    private val effectHandlersGetOperationHandlerDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_get_operation_handler",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = operationHandlerType,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler"),
            LlvmParameter(type = operationIndexType, name = "operation_index"),
        )
    )

    private fun effectHandlersGetOperationHandler(
        target: LlvmOperandLocal,
        effectHandler: LlvmOperand,
        operationIndex: Int,
    ): List<LlvmInstruction> {
        val call = effectHandlersGetOperationHandlerDeclaration.call(
            target = target,
            arguments = listOf(
                effectHandler,
                LlvmOperandInt(operationIndex),
            )
        )
        return listOf(call)
    }

    private val operationHandlerExitDeclaration = LlvmFunctionDeclaration(
        name = "shed_operation_handler_exit",
        returnType = LlvmTypes.void,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler")
        ),
        attributes = listOf(LlvmFunctionAttribute.NO_RETURN),
    )

    private fun callOperationHandler(
        target: LlvmOperandLocal,
        effectHandler: LlvmOperand,
        operationHandler: LlvmOperand,
        hasState: Boolean,
        arguments: List<LlvmOperand>,
    ): List<LlvmInstruction> {
        val (stateArguments, stateInstructions) = if (hasState) {
            val stateOperand = irBuilder.generateLocal("state")
            Pair(listOf(stateOperand), listOf(getState(effectHandler = effectHandler, target = stateOperand)))
        } else {
            Pair(listOf(), listOf())
        }

        return persistentListOf<LlvmInstruction>()
            .addAll(stateInstructions)
            .addAll(closures.callClosure(
                target = target,
                closurePointer = operationHandler,
                prefixArguments = listOf(LlvmTypedOperand(
                    type = effectHandlerType,
                    operand = effectHandler
                )),
                arguments = stateArguments + arguments
            ))
    }

    private val effectHandlersEnterDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_enter",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = effectHandlerType,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler")
        )
    )

    private fun enter(target: LlvmOperandLocal, effectHandler: LlvmOperand): LlvmInstruction {
        return effectHandlersEnterDeclaration.call(
            target = target,
            arguments = listOf(effectHandler)
        )
    }

    private val effectHandlersRestoreDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_restore",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler")
        )
    )

    private fun restore(effectHandler: LlvmOperand): LlvmInstruction {
        return effectHandlersRestoreDeclaration.call(
            target = null,
            arguments = listOf(effectHandler)
        )
    }

    private val operationHandlerValueDeclaration = LlvmGlobalDefinition(
        name = "shed_operation_handler_value",
        type = compiledValueType,
        value = LlvmOperandInt(0)
    )

    private fun loadOperationHandlerValue(target: LlvmVariable): LlvmLoad {
        return LlvmLoad(
            target = target,
            type = compiledValueType,
            pointer = LlvmOperandGlobal(operationHandlerValueDeclaration.name)
        )
    }

    private fun storeOperationHandlerValue(value: LlvmOperand): LlvmStore {
        return LlvmStore(
            type = compiledValueType,
            pointer = LlvmOperandGlobal(operationHandlerValueDeclaration.name),
            value = value,
        )
    }

    internal fun exit(value: LlvmOperand): List<LlvmInstruction> {
        return listOf(
            storeOperationHandlerValue(value),
            operationHandlerExitDeclaration.call(
                target = null,
                arguments = listOf(LlvmOperandLocal("effect_handler")),
            ),
            LlvmUnreachable,
        )
    }

    internal fun resume(value: LlvmOperand): List<LlvmInstruction> {
        return listOf(
            LlvmReturn(type = LlvmTypes.i64, value = value),
        )
    }

    internal fun resumeWithState(value: LlvmOperand, newState: LlvmOperand): List<LlvmInstruction> {
        return listOf(
            setState(LlvmOperandLocal("effect_handler"), newState),
            LlvmReturn(type = LlvmTypes.i64, value = value),
        )
    }

    private val effectHandlersSetStateDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_set_state",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler"),
            LlvmParameter(type = compiledValueType, name = "state"),
        )
    )

    private fun setState(effectHandler: LlvmOperand, newState: LlvmOperand): LlvmCall {
        return effectHandlersSetStateDeclaration.call(
            target = null,
            arguments = listOf(effectHandler, newState),
        )
    }

    private val effectHandlersGetStateDeclaration = LlvmFunctionDeclaration(
        name = "shed_effects_get_state",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = compiledValueType,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler"),
        )
    )

    private fun getState(effectHandler: LlvmOperand, target: LlvmVariable): LlvmCall {
        return effectHandlersGetStateDeclaration.call(
            target = target,
            arguments = listOf(effectHandler),
        )
    }

    internal fun declarations(): List<LlvmTopLevelEntity> {
        return listOf(
            effectHandlersSetOperationHandlerDeclaration,
            effectHandlersSetStateDeclaration,
            effectHandlersGetStateDeclaration,
            effectHandlersPushDeclaration,
            effectHandlersDiscardDeclaration,
            effectHandlersFindEffectHandlerDeclaration,
            effectHandlersGetOperationHandlerDeclaration,
            effectHandlersOperationHandlerGetFunctionDeclaration,
            effectHandlersOperationHandlerGetContextDeclaration,
            effectHandlersEnterDeclaration,
            effectHandlersRestoreDeclaration,
            operationHandlerExitDeclaration,
            operationHandlerValueDeclaration
        )
    }

    internal fun closurePrefixParameters(): List<LlvmParameter> {
        return listOf(LlvmParameter(type = effectHandlerType, name = "effect_handler"))
    }
}
