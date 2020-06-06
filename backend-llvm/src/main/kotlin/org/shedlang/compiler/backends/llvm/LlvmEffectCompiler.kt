package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.ComputationalEffect
import org.shedlang.compiler.types.FunctionType

internal class EffectCompiler(private val irBuilder: LlvmIrBuilder) {
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
            LlvmParameter(type = CTypes.voidPointer, name = "operation_handlers")
        )
    )

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

    internal fun declarations(): List<LlvmTopLevelEntity> {
        return listOf(
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
}
