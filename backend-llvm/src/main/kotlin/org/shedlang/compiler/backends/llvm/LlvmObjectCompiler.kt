package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.StaticValue
import org.shedlang.compiler.types.TagValue

internal class LlvmObjectCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler
) {
    internal fun createObject(
        objectType: StaticValue,
        fields: List<Pair<Identifier, LlvmOperand>>,
        target: LlvmOperandLocal
    ): List<LlvmInstruction> {
        val instance = LlvmOperandLocal(irBuilder.generateName("instance"))
        val compiledObjectType = compiledType(objectType = objectType)
        return listOf(
            libc.typedMalloc(
                target = instance,
                bytes = compiledObjectType.byteSize(),
                type = compiledObjectType.llvmPointerType()
            ),

            storeObject(
                fields = fields,
                objectType = objectType,
                objectPointer = instance
            ),

            listOf(
                LlvmPtrToInt(
                    target = target,
                    sourceType = compiledObjectType.llvmPointerType(),
                    value = instance,
                    targetType = compiledValueType
                )
            )
        ).flatten()
    }

    internal fun storeObject(
        fields: List<Pair<Identifier, LlvmOperand>>,
        objectType: StaticValue,
        objectPointer: LlvmOperand
    ): List<LlvmInstruction> {
        val compiledObjectType = compiledType(objectType = objectType)

        val tagValue = compiledObjectType.tagValue
        val storeTagValue = if (tagValue == null) {
            listOf()
        } else {
            val tagValuePointer = LlvmOperandLocal(irBuilder.generateName("tagValuePointer"))

            listOf(
                tagValuePointer(
                    target = tagValuePointer,
                    source = objectPointer,
                    sourceType = compiledObjectType.llvmPointerType()
                ),
                LlvmStore(
                    type = compiledTagValueType,
                    value = LlvmOperandInt(tagValueToInt(tagValue)),
                    pointer = tagValuePointer
                )
            )
        }

        val storeFields = fields.flatMap { (fieldName, fieldValue) ->
            val fieldPointer = LlvmOperandLocal(irBuilder.generateName("fieldPointer"))

            listOf(
                fieldPointer(
                    target = fieldPointer,
                    receiver = objectPointer,
                    receiverType = objectType,
                    fieldName = fieldName
                ),
                LlvmStore(
                    type = compiledValueType,
                    value = fieldValue,
                    pointer = fieldPointer
                )
            )
        }

        return storeTagValue + storeFields
    }

    internal fun fieldAccess(receiver: LlvmOperand, fieldName: Identifier, receiverType: StaticValue, target: LlvmVariable): List<LlvmInstruction> {
        val fieldPointerVariable = LlvmOperandLocal(irBuilder.generateName("fieldPointer"))

        return listOf(
            fieldPointer(
                target = fieldPointerVariable,
                receiver = receiver,
                receiverType = receiverType,
                fieldName = fieldName
            ),
            LlvmLoad(
                target = target,
                type = compiledValueType,
                pointer = fieldPointerVariable
            )
        )
    }

    private fun fieldPointer(target: LlvmOperandLocal, receiver: LlvmOperand, receiverType: StaticValue, fieldName: Identifier): LlvmGetElementPtr {
        val compiledObjectType = compiledType(objectType = receiverType)
        return compiledObjectType.getFieldPointer(target = target, receiver = receiver, fieldName = fieldName)
    }

    internal fun tagValuePointer(
        target: LlvmOperandLocal,
        source: LlvmOperand,
        sourceType: LlvmTypePointer
    ): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = sourceType,
            pointer = source,
            indices = listOf(
                LlvmIndex.i32(0),
                LlvmIndex.i32(0)
            )
        )
    }

    private var nextTagValueInt = 1
    private val tagValueToInt: MutableMap<TagValue, Int> = mutableMapOf()

    internal fun tagValueToInt(tagValue: TagValue): Int {
        if (!tagValueToInt.containsKey(tagValue)) {
            tagValueToInt[tagValue] = nextTagValueInt++
        }

        return tagValueToInt.getValue(tagValue)
    }
}
