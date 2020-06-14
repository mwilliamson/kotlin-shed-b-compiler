package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.ComputationalEffect
import org.shedlang.compiler.types.FunctionType

internal class EffectCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler
) {
    private val effectIdType = LlvmTypes.i32
    private val operationIndexType = CTypes.size_t
    private val operationArgumentsPointerType = LlvmTypes.pointer(LlvmTypes.arrayType(size = 0, elementType = compiledValueType))

    internal fun compiledOperationArgumentsType(operationType: FunctionType): LlvmType {
        val parameterCount = operationType.positionalParameters.size + operationType.namedParameters.size
        return LlvmTypes.arrayType(size = parameterCount, elementType = compiledValueType)
    }

    private val effectHandlersPushDeclaration = LlvmFunctionDeclaration(
        name = "shed_effect_handlers_push",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.void,
        parameters = listOf(
            LlvmParameter(type = effectIdType, name = "effect_id"),
            LlvmParameter(type = CTypes.jmpBufPointer, name = "env")
        )
    )

    internal fun effectHandlersPush(effectId: Int, env: LlvmOperand): LlvmCall {
        return LlvmCall(
            target = null,
            functionPointer = LlvmOperandGlobal(effectHandlersPushDeclaration.name),
            callingConvention = effectHandlersPushDeclaration.callingConvention,
            returnType = effectHandlersPushDeclaration.returnType,
            arguments = listOf(
                LlvmTypedOperand(effectIdType, LlvmOperandInt(effectId)),
                LlvmTypedOperand(CTypes.jmpBufPointer, env)
            )
        )
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

    private val allocJmpBufDeclaration = LlvmFunctionDeclaration(
        name = "alloc_jmp_buf",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.jmpBufPointer,
        parameters = listOf()
    )

    private fun allocJmpBuf(target: LlvmVariable): LlvmCall {
        return LlvmCall(
            target = target,
            functionPointer = LlvmOperandGlobal(allocJmpBufDeclaration.name),
            callingConvention = allocJmpBufDeclaration.callingConvention,
            returnType = allocJmpBufDeclaration.returnType,
            arguments = listOf()
        )
    }

    internal fun setjmp(env: LlvmOperandLocal, target: LlvmOperandLocal): List<LlvmInstruction> {
        return listOf(
            allocJmpBuf(target = env),
            libc.setjmp(
                target = target,
                env = env
            )
        )
    }

    internal fun declarations(): List<LlvmTopLevelEntity> {
        return listOf(
            allocJmpBufDeclaration,
            effectHandlersPushDeclaration,
            effectHandlersDiscardDeclaration,
            effectHandlersCallDeclaration,
            LlvmGlobalDefinition(
                name = "shed_jmp_buf",
                type = LlvmTypes.i8,
                value = null
            ),
            LlvmGlobalDefinition(
                name = "active_operation_arguments",
                type = LlvmTypes.pointer(LlvmTypes.arrayType(0, compiledValueType)),
                value = null
            )
        )
    }

    internal fun loadArguments(packedArgumentsPointer: LlvmOperand, parameterCount: Int): Pair<List<LlvmOperandLocal>, List<LlvmInstruction>> {
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
