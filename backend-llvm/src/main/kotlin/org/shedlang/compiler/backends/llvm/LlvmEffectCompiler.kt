package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.HandlerNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.ComputationalEffect
import org.shedlang.compiler.types.FunctionType

internal class EffectCompiler(
    private val closures: ClosureCompiler,
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler
) {
    private val effectIdType = LlvmTypes.i32
    private val effectHandlerType = CTypes.voidPointer
    private val operationHandlerType = CTypes.voidPointer
    private val operationIndexType = CTypes.size_t
    private val operationArgumentsPointerType = LlvmTypes.pointer(LlvmTypes.arrayType(size = 0, elementType = compiledValueType))

    internal fun compiledOperationArgumentsType(operationType: FunctionType): LlvmType {
        val parameterCount = operationType.positionalParameters.size + operationType.namedParameters.size
        return LlvmTypes.arrayType(size = parameterCount, elementType = compiledValueType)
    }

    internal fun handle(
        effect: ComputationalEffect,
        compileBody: (FunctionContext) -> FunctionContext,
        handlerTypes: List<HandlerNode.Type>,
        context: FunctionContext
    ): FunctionContext {
        val (context2, operationHandlers) = context.popTemporaries(effect.operations.size)

        val setjmpResult = irBuilder.generateLocal("setjmpResult")
        val setjmpEnv = irBuilder.generateLocal("setjmpEnv")
        val normalLabel = irBuilder.generateName("normal")
        val untilLabel = irBuilder.generateName("until")
        val exitLabel = irBuilder.generateName("exit")
        val exitResult = irBuilder.generateLocal("exitResult")

        return context2
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
                    handlerTypes = handlerTypes,
                    setjmpEnv = setjmpEnv,
                    context = it
                )
            }
            .let { compileBody(it) }
            .addInstructions(effectHandlersDiscard())
            .addInstructions(LlvmBrUnconditional(untilLabel))
            .addInstructions(LlvmLabel(exitLabel))
            .addInstructions(loadExitValue(target = exitResult))
            .pushTemporary(exitResult)
            .addInstructions(LlvmBrUnconditional(untilLabel))
            .addInstructions(LlvmLabel(untilLabel))
    }

    private val effectHandlersSetOperationHandlerDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_set_operation_handler",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = effectHandlerType,
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
        return libc.call(
            target = null,
            function = effectHandlersSetOperationHandlerDeclaration,
            arguments = listOf(
                effectHandler,
                LlvmOperandInt(operationIndex),
                operationHandlerFunction,
                operationHandlerContext
            )
        )
    }

    private val effectHandlersPushDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_push",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = effectHandlerType,
        parameters = listOf(
            LlvmParameter(type = effectIdType, name = "effect_id"),
            LlvmParameter(type = CTypes.int, name = "operation_count"),
            LlvmParameter(type = CTypes.jmpBufPointer, name = "env")
        )
    )

    private fun effectHandlersPush(
        effect: ComputationalEffect,
        operationHandlers: List<LlvmOperand>,
        handlerTypes: List<HandlerNode.Type>,
        setjmpEnv: LlvmOperand,
        context: FunctionContext
    ): FunctionContext {
        val effectHandler = irBuilder.generateLocal("effectHandler")

        val operations = effect.operations.entries.sortedBy { (operationName, _) -> operationName }
        val operationTypes = operations.map { (_, operationType) -> operationType }

        return context
            .addInstructions(libc.call(
                target = effectHandler,
                function = effectHandlersPushDeclaration,
                arguments = listOf(
                    LlvmOperandInt(effect.definitionId),
                    LlvmOperandInt(effect.operations.size),
                    setjmpEnv
                )
            ))
            .let {
                handlerTypes.foldIndexed(it) { operationIndex, context, handlerType ->
                    val operationHandlerName = irBuilder.generateName("operationHandler")
                    val operationHandlerAsVoidPointer = irBuilder.generateLocal("operationHandlerAsVoidPointer")
                    val operationHandlerResult = irBuilder.generateLocal("operationHandlerResult")
                    val operationType = operationTypes[operationIndex]
                    val closure = irBuilder.generateLocal("operationHandlerClosure")
                    val previousEffectHandlerStack = irBuilder.generateLocal("previousEffectHandlerStack")

                    val returnInstructions = when (handlerType) {
                        HandlerNode.Type.EXIT ->
                            listOf(
                                libc.call(
                                    target = null,
                                    function = operationHandlerExitDeclaration,
                                    arguments = listOf(
                                        LlvmOperandLocal("effect_handler"),
                                        operationHandlerResult
                                    )
                                ),
                                LlvmUnreachable
                            )

                        HandlerNode.Type.RESUME ->
                            listOf(
                                restore(previousEffectHandlerStack),
                                LlvmReturn(compiledValueType, operationHandlerResult)
                            )
                    }

                    context
                        .addTopLevelEntities(LlvmFunctionDefinition(
                            name = operationHandlerName,
                            returnType = compiledValueType,
                            parameters = listOf(
                                LlvmParameter(effectHandlerType, "effect_handler"),
                                LlvmParameter(compiledValueType, "context"),
                                LlvmParameter(LlvmTypes.pointer(LlvmTypes.arrayType(0, compiledValueType)), "operation_arguments")
                            ),
                            body = persistentListOf<LlvmInstruction>()
                                .add(enter(
                                    target = previousEffectHandlerStack,
                                    effectHandler = LlvmOperandLocal("effect_handler")
                                ))
                                .addAll(callOperationHandler(
                                    target = operationHandlerResult,
                                    operationHandler = LlvmOperandLocal("context"),
                                    operationType = operationType,
                                    packedArgumentsPointer = LlvmOperandLocal("operation_arguments")
                                ))
                                .addAll(returnInstructions)
                        ))
                        .addInstructions(LlvmBitCast(
                            target = operationHandlerAsVoidPointer,
                            targetType = operationHandlerType,
                            value = LlvmOperandGlobal(operationHandlerName),
                            sourceType = LlvmTypes.pointer(LlvmTypes.function(
                                parameterTypes = listOf(
                                    effectHandlerType,
                                    compiledValueType,
                                    LlvmTypes.pointer(LlvmTypes.arrayType(0, compiledValueType))
                                ),
                                returnType = compiledValueType
                            ))
                        ))
                        .addInstructions(LlvmIntToPtr(
                            target = closure,
                            targetType = CTypes.voidPointer,
                            value = operationHandlers[operationIndex],
                            sourceType = compiledValueType
                        ))
                        .addInstructions(effectHandlersSetOperationHandler(
                            effectHandler = effectHandler,
                            operationIndex = operationIndex,
                            operationHandlerFunction = operationHandlerAsVoidPointer,
                            operationHandlerContext = closure
                        ))
                }
            }
    }

    private val effectHandlersDiscardDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_discard",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf()
    )

    private fun effectHandlersDiscard(): LlvmCall {
        return libc.call(
            target = null,
            function = effectHandlersDiscardDeclaration,
            arguments = listOf()
        )
    }

    private val effectHandlersCallDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_call",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = compiledValueType,
        parameters = listOf(
            LlvmParameter(type = effectIdType, name = "effect_id"),
            LlvmParameter(type = operationIndexType, name = "operation_index"),
            LlvmParameter(type = operationArgumentsPointerType, name = "arguments")
        )
    )

    internal fun effectHandlersCall(
        target: LlvmOperandLocal,
        effect: ComputationalEffect,
        operationName: Identifier,
        operationArguments: LlvmTypedOperand
    ): List<LlvmInstruction> {
        val argumentsPointer = irBuilder.generateLocal("arguments")
        val cast = LlvmBitCast(
            target = argumentsPointer,
            sourceType = operationArguments.type,
            value = operationArguments.operand,
            targetType = operationArgumentsPointerType
        )
        val operationIndex = effect.operations.keys.sorted().indexOf(operationName)
        val call = libc.call(
            target = target,
            function = effectHandlersCallDeclaration,
            arguments = listOf(
                LlvmOperandInt(effect.definitionId),
                LlvmOperandInt(operationIndex),
                argumentsPointer
            )
        )
        return listOf(cast, call)
    }

    private val operationHandlerExitDeclaration = LlvmFunctionDeclaration(
        name = "shed_operation_handler_exit",
        returnType = LlvmTypes.void,
        parameters = listOf(
            LlvmParameter(effectHandlerType, "effect_handler"),
            LlvmParameter(compiledValueType, "exit_value")
        ),
        noReturn = true
    )

    private fun callOperationHandler(
        target: LlvmOperandLocal,
        operationHandler: LlvmOperand,
        operationType: FunctionType,
        packedArgumentsPointer: LlvmOperand
    ): List<LlvmInstruction> {
        val parameterCount = operationType.positionalParameters.size + operationType.namedParameters.size
        val (arguments, argumentInstructions) = unpackArguments(
            packedArgumentsPointer = packedArgumentsPointer,
            parameterCount = parameterCount
        )

        return persistentListOf<LlvmInstruction>()
            .addAll(argumentInstructions)
            .addAll(closures.callClosure(
                target = target,
                closurePointer = operationHandler,
                arguments = (0 until parameterCount).map { argumentIndex ->
                    LlvmTypedOperand(
                        type = compiledValueType,
                        operand = arguments[argumentIndex]
                    )
                }
            ))
    }

    private fun unpackArguments(
        packedArgumentsPointer: LlvmOperand,
        parameterCount: Int
    ): Pair<List<LlvmOperandLocal>, List<LlvmInstruction>> {
        val arguments = (0 until parameterCount).map { argumentIndex ->
            irBuilder.generateLocal("arg" + argumentIndex)
        }
        val argumentInstructions = (0 until parameterCount).flatMap { argumentIndex ->
            val packageArgumentPointer = irBuilder.generateLocal("argPointer" + argumentIndex)
            listOf(
                LlvmGetElementPtr(
                    target = packageArgumentPointer,
                    pointerType = LlvmTypes.pointer(LlvmTypes.arrayType(0, compiledValueType)),
                    pointer = packedArgumentsPointer,
                    indices = listOf(LlvmIndex.i32(0), LlvmIndex.i32(argumentIndex))
                ),
                LlvmLoad(
                    target = arguments[argumentIndex],
                    pointer = packageArgumentPointer,
                    type = compiledValueType
                )
            )
        }
        return Pair(arguments, argumentInstructions)
    }

    private val effectHandlersEnterDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_enter",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = effectHandlerType,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler")
        )
    )

    private fun enter(target: LlvmOperandLocal, effectHandler: LlvmOperand): LlvmInstruction {
        return libc.call(
            target = target,
            function = effectHandlersEnterDeclaration,
            arguments = listOf(effectHandler)
        )
    }

    private val effectHandlersRestoreDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_restore",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf(
            LlvmParameter(type = effectHandlerType, name = "effect_handler")
        )
    )

    private fun restore(effectHandler: LlvmOperand): LlvmInstruction {
        return libc.call(
            target = null,
            function = effectHandlersRestoreDeclaration,
            arguments = listOf(effectHandler)
        )
    }

    private val exitValueDeclaration = LlvmGlobalDefinition(
        name = "shed_exit_value",
        type = compiledValueType,
        value = null
    )

    private fun loadExitValue(target: LlvmVariable): LlvmLoad {
        return LlvmLoad(
            target = target,
            type = compiledValueType,
            pointer = LlvmOperandGlobal(exitValueDeclaration.name)
        )
    }

    internal fun declarations(): List<LlvmTopLevelEntity> {
        return listOf(
            effectHandlersSetOperationHandlerDeclaration,
            effectHandlersPushDeclaration,
            effectHandlersDiscardDeclaration,
            effectHandlersCallDeclaration,
            effectHandlersEnterDeclaration,
            effectHandlersRestoreDeclaration,
            operationHandlerExitDeclaration,
            exitValueDeclaration
        )
    }
}
