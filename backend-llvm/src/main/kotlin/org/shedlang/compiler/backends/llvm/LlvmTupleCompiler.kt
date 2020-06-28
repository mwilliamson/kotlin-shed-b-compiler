package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.flatMapIndexed

internal class LlvmTupleCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler
) {
    internal fun tupleCreate(target: LlvmOperandLocal, elements: List<LlvmOperand>): List<LlvmInstruction> {
        val tuple = irBuilder.generateLocal("tuple")

        val malloc = libc.typedMalloc(
            target = tuple,
            bytes = compiledTupleType.byteSize,
            type = compiledTupleType
        )

        val storeElements = elements.flatMapIndexed { elementIndex, element ->
            val elementPointer = irBuilder.generateLocal("element")
            listOf(
                tupleElementPointer(elementPointer, tuple, elementIndex),
                LlvmStore(
                    type = compiledValueType,
                    value = element,
                    pointer = elementPointer
                )
            )
        }

        val cast = LlvmPtrToInt(
            target = target,
            sourceType = compiledTupleType,
            value = tuple,
            targetType = compiledValueType
        )

        return malloc + storeElements + listOf(cast)
    }

    internal fun tupleAccess(
        target: LlvmOperandLocal,
        receiver: LlvmOperand,
        elementIndex: Int
    ): List<LlvmInstruction> {

        val tuple = irBuilder.generateLocal("tuple")
        val elementPointer = irBuilder.generateLocal("elementPointer")

        return listOf(
            LlvmIntToPtr(
                target = tuple,
                sourceType = compiledValueType,
                value = receiver,
                targetType = compiledTupleType
            ),
            tupleElementPointer(elementPointer, tuple, elementIndex),
            LlvmLoad(
                target = target,
                type = compiledValueType,
                pointer = elementPointer
            )
        )
    }

    private fun tupleElementPointer(target: LlvmOperandLocal, receiver: LlvmOperandLocal, elementIndex: Int): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledTupleType,
            pointer = receiver,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(elementIndex))
            )
        )
    }
}
