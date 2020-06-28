package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.HandlerNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.flatMapIndexed
import org.shedlang.compiler.types.FunctionType
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
    private val operationIndexType = CTypes.size_t
    private val operationArgumentsPointerType = LlvmTypes.pointer(LlvmTypes.arrayType(size = 0, elementType = compiledValueType))

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
                    val functionName = irBuilder.generateName(operationName)
                    val parameterCount = operationType.positionalParameters.size + operationType.namedParameters.size
                    val returnValue = irBuilder.generateLocal("return")
                    val closureEnvironmentParameter = LlvmParameter(
                        compiledClosureEnvironmentPointerType,
                        irBuilder.generateName("environment")
                    )
                    val llvmParameters = (0 until parameterCount).map { parameterIndex ->
                        LlvmParameter(type = compiledValueType, name = "arg" + parameterIndex)
                    }
                    val operationArguments = irBuilder.generateLocal("arguments")
                    val operationArgumentsType = compiledOperationArgumentsType(operationType)

                    closures.createClosure(
                        target = operationOperands.getValue(operationName),
                        functionName = functionName,
                        parameterTypes = llvmParameters.map { llvmParameter -> llvmParameter.type },
                        freeVariables = listOf(),
                        context = context2
                    ).addTopLevelEntities(LlvmFunctionDefinition(
                        name = functionName,
                        returnType = compiledValueType,
                        parameters = listOf(closureEnvironmentParameter) + llvmParameters,
                        body = persistentListOf<LlvmInstruction>()
                            .addAll(packArguments(
                                target = operationArguments,
                                arguments = llvmParameters.map { llvmParameter ->
                                    LlvmOperandLocal(llvmParameter.name)
                                }
                            ))
                            .addAll(effectHandlersCall(
                                target = returnValue,
                                effect = effect,
                                operationName = operationName,
                                operationArguments = LlvmTypedOperand(
                                    type = LlvmTypes.pointer(operationArgumentsType),
                                    operand = operationArguments
                                )
                            ))
                            .add(LlvmReturn(type = compiledValueType, value = returnValue))
                    ))
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

    private fun compiledOperationArgumentsType(operationType: FunctionType): LlvmType {
        val parameterCount = operationType.positionalParameters.size + operationType.namedParameters.size
        return compiledOperationArgumentsType(parameterCount)
    }

    private fun compiledOperationArgumentsType(argumentCount: Int): LlvmType {
        return LlvmTypes.arrayType(size = argumentCount, elementType = compiledValueType)
    }

    internal fun handle(
        effect: UserDefinedEffect,
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
        effect: UserDefinedEffect,
        operationHandlers: List<LlvmOperand>,
        handlerTypes: List<HandlerNode.Type>,
        setjmpEnv: LlvmOperand,
        context: FunctionContext
    ): FunctionContext {
        val effectHandler = irBuilder.generateLocal("effectHandler")

        val operations = effect.operations.entries.sortedBy { (operationName, _) -> operationName }
        val operationTypes = operations.map { (_, operationType) -> operationType }

        return context
            .addInstructions(effectHandlersPushDeclaration.call(
                target = effectHandler,
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
                                operationHandlerExitDeclaration.call(
                                    target = null,
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
        return effectHandlersDiscardDeclaration.call(
            target = null,
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

    private fun effectHandlersCall(
        target: LlvmOperandLocal,
        effect: UserDefinedEffect,
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
        val call = effectHandlersCallDeclaration.call(
            target = target,
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
                arguments = arguments
            ))
    }

    private fun packArguments(
        target: LlvmOperandLocal,
        arguments: List<LlvmOperand>
    ): PersistentList<LlvmInstruction> {
        val operationArgumentsType = compiledOperationArgumentsType(arguments.size)

        return persistentListOf<LlvmInstruction>()
            .addAll(libc.typedMalloc(
                target = target,
                bytes = operationArgumentsType.byteSize,
                type = LlvmTypes.pointer(operationArgumentsType)
            ))
            .addAll(arguments.flatMapIndexed { argumentIndex, argument ->
                val operationArgumentPointer = irBuilder.generateLocal("operationArgumentPointer")
                persistentListOf<LlvmInstruction>()
                    .add(LlvmGetElementPtr(
                        target = operationArgumentPointer,
                        pointerType = LlvmTypes.pointer(operationArgumentsType),
                        pointer = target,
                        indices = listOf(
                            LlvmIndex.i64(0),
                            LlvmIndex.i64(argumentIndex)
                        )
                    ))
                    .add(LlvmStore(
                        type = compiledValueType,
                        value = argument,
                        pointer = operationArgumentPointer
                    ))
            })
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
        return effectHandlersEnterDeclaration.call(
            target = target,
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
        return effectHandlersRestoreDeclaration.call(
            target = null,
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

    internal fun resume(value: LlvmOperand): LlvmInstruction {
        return LlvmReturn(type = compiledValueType, value = value)
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
