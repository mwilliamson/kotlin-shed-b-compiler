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
        return LlvmCall(
            target = null,
            functionPointer = LlvmOperandGlobal(effectHandlersSetOperationHandlerDeclaration.name),
            callingConvention = effectHandlersSetOperationHandlerDeclaration.callingConvention,
            returnType = effectHandlersSetOperationHandlerDeclaration.returnType,
            arguments = listOf(
                LlvmTypedOperand(effectHandlerType, effectHandler),
                LlvmTypedOperand(operationIndexType, LlvmOperandInt(operationIndex)),
                LlvmTypedOperand(operationHandlerType, operationHandlerFunction),
                LlvmTypedOperand(CTypes.voidPointer, operationHandlerContext)
            )
        )
    }

    private val effectHandlersPushDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_push",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = effectHandlerType,
        parameters = listOf(
            LlvmParameter(type = effectIdType, name = "effect_id"),
            LlvmParameter(type = CTypes.int, name = "operation_count")
        )
    )

    internal fun effectHandlersPush(
        effect: ComputationalEffect,
        operationHandlers: List<LlvmOperand>,
        handlerTypes: List<HandlerNode.Type>,
        setjmpEnv: LlvmOperand,
        context: FunctionContext
    ): FunctionContext {
        val effectHandler = irBuilder.generateLocal("effectHandler")
        val setjmpEnvAsVoidPointer = irBuilder.generateLocal("setjmpEnvVoidPointer")

        val operations = effect.operations.entries.sortedBy { (operationName, _) -> operationName }
        val operationTypes = operations.map { (_, operationType) -> operationType }

        return context
            .addInstructions(LlvmBitCast(
                target = setjmpEnvAsVoidPointer,
                targetType = CTypes.voidPointer,
                value = setjmpEnv,
                sourceType = CTypes.jmpBufPointer
            ))
            .addInstructions(LlvmCall(
                target = effectHandler,
                functionPointer = LlvmOperandGlobal(effectHandlersPushDeclaration.name),
                callingConvention = effectHandlersPushDeclaration.callingConvention,
                returnType = effectHandlersPushDeclaration.returnType,
                arguments = listOf(
                    LlvmTypedOperand(effectIdType, LlvmOperandInt(effect.definitionId)),
                    LlvmTypedOperand(CTypes.int, LlvmOperandInt(effect.operations.size))
                )
            ))
            .let {
                handlerTypes.foldIndexed(it) { operationIndex, context, handlerType ->
                    when (handlerType) {
                        HandlerNode.Type.EXIT ->
                            context.addInstructions(effectHandlersSetOperationHandler(
                                effectHandler = effectHandler,
                                operationIndex = operationIndex,
                                operationHandlerFunction = LlvmOperandGlobal(operationHandlerExitDeclaration.name),
                                operationHandlerContext = setjmpEnvAsVoidPointer
                            ))

                        HandlerNode.Type.RESUME -> {
                            // TODO: handle swapping of effect handler stack
                            val operationHandlerName = irBuilder.generateName("operationHandler")
                            val operationHandlerAsVoidPointer = irBuilder.generateLocal("operationHandlerAsVoidPointer")
                            val operationHandlerResult = irBuilder.generateLocal("operationHandlerResult")
                            val operationType = operationTypes[operationIndex]
                            val closure = irBuilder.generateLocal("operationHandlerClosure")
                            context
                                .addTopLevelEntities(LlvmFunctionDefinition(
                                    name = operationHandlerName,
                                    returnType = compiledValueType,
                                    parameters = listOf(
                                        LlvmParameter(effectHandlerType, "effect_handler"),
                                        LlvmParameter(operationIndexType, "operation_index"),
                                        LlvmParameter(compiledValueType, "context"),
                                        LlvmParameter(LlvmTypes.pointer(LlvmTypes.arrayType(0, compiledValueType)), "operation_arguments")
                                    ),
                                    body = persistentListOf<LlvmInstruction>()
                                        .addAll(callOperationHandler(
                                            target = operationHandlerResult,
                                            operationHandler = LlvmOperandLocal("context"),
                                            operationType = operationType,
                                            packedArgumentsPointer = LlvmOperandLocal("operation_arguments")
                                        ))
                                        .add(LlvmReturn(compiledValueType, operationHandlerResult))
                                ))
                                .addInstructions(LlvmBitCast(
                                    target = operationHandlerAsVoidPointer,
                                    targetType = operationHandlerType,
                                    value = LlvmOperandGlobal(operationHandlerName),
                                    sourceType = LlvmTypes.pointer(LlvmTypes.function(
                                        parameterTypes = listOf(
                                            effectHandlerType,
                                            operationIndexType,
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
            }
    }

    private val effectHandlersDiscardDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_discard",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf()
    )

    internal fun effectHandlersDiscard(): LlvmCall {
        return LlvmCall(
            target = null,
            functionPointer = LlvmOperandGlobal(effectHandlersDiscardDeclaration.name),
            callingConvention = effectHandlersDiscardDeclaration.callingConvention,
            returnType = effectHandlersDiscardDeclaration.returnType,
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
        val call = LlvmCall(
            target = target,
            functionPointer = LlvmOperandGlobal(effectHandlersCallDeclaration.name),
            callingConvention = effectHandlersCallDeclaration.callingConvention,
            returnType = effectHandlersCallDeclaration.returnType,
            arguments = listOf(
                LlvmTypedOperand(effectIdType, LlvmOperandInt(effect.definitionId)),
                LlvmTypedOperand(operationIndexType, LlvmOperandInt(operationIndex)),
                LlvmTypedOperand(operationArgumentsPointerType, argumentsPointer)
            )
        )
        return listOf(cast, call)
    }

    private val operationHandlerExitDeclaration = LlvmGlobalDefinition(
        name = "shed_operation_handler_exit",
        type = operationHandlerType.type,
        value = null
    )

    internal fun declarations(): List<LlvmTopLevelEntity> {
        return listOf(
            effectHandlersSetOperationHandlerDeclaration,
            effectHandlersPushDeclaration,
            effectHandlersDiscardDeclaration,
            effectHandlersCallDeclaration,
            operationHandlerExitDeclaration,
            LlvmGlobalDefinition(
                name = "active_operation_arguments",
                type = LlvmTypes.pointer(LlvmTypes.arrayType(0, compiledValueType)),
                value = null
            )
        )
    }

    internal fun callOperationHandler(
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
}
