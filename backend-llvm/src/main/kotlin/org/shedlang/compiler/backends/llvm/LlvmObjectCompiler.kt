package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.*

internal class LlvmObjectCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val libc: LibcCallCompiler
) {
    internal fun createObject(
        objectType: TypeLevelValue,
        fields: List<Pair<Identifier, LlvmOperand>>,
        target: LlvmOperandLocal
    ): List<LlvmInstruction> {
        val instance = irBuilder.generateLocal("instance")
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
        objectType: TypeLevelValue,
        objectPointer: LlvmOperand
    ): List<LlvmInstruction> {
        val compiledObjectType = compiledType(objectType = objectType)

        val tagValue = compiledObjectType.tagValue
        val storeTagValue = if (tagValue == null) {
            listOf()
        } else {
            val tagValuePointer = irBuilder.generateLocal("tagValuePointer")

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
            val fieldPointer = irBuilder.generateLocal("fieldPointer")

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


    internal fun updateObject(
        objectType: TypeLevelValue,
        existingObjectOperand: LlvmOperand,
        updatedFieldName: Identifier,
        updatedFieldValue: LlvmOperand,
        target: LlvmOperandLocal
    ): List<LlvmInstruction> {
        val existingObject = irBuilder.generateLocal("existing")
        val instance = irBuilder.generateLocal("instance")
        val compiledObjectType = compiledType(objectType = objectType)
        val shapeType = objectType as SimpleShapeType
        val newPopulatedFieldNames = shapeType.fields.keys + setOf(updatedFieldName)

        val malloc = libc.typedMalloc(
            target = instance,
            bytes = compiledObjectType.byteSize(),
            type = compiledObjectType.llvmPointerType()
        )
        val (fieldTargets, getFieldInstructions) = newPopulatedFieldNames.map { fieldName ->
            if (fieldName == updatedFieldName) {
                updatedFieldValue to listOf()
            } else {
                val fieldTarget = irBuilder.generateLocal(fieldName)
                fieldTarget to fieldAccess(
                    target = fieldTarget,
                    receiver = existingObject,
                    receiverType = objectType,
                    fieldName = fieldName,
                )
            }
        }.unzip()

        return listOf(
            malloc,

            listOf(castToObjectPointer(
                target = existingObject,
                value = existingObjectOperand,
                objectType = objectType,
            )),
            getFieldInstructions.flatten(),

            storeObject(
                fields = newPopulatedFieldNames.zip(fieldTargets),
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

    internal fun castToObjectPointer(target: LlvmOperandLocal, objectType: TypeLevelValue, value: LlvmOperand): LlvmIntToPtr {
        return LlvmIntToPtr(
            target = target,
            sourceType = compiledValueType,
            value = value,
            targetType = compiledType(objectType = objectType).llvmPointerType()
        )
    }

    internal fun fieldAccess(receiver: LlvmOperand, fieldName: Identifier, receiverType: TypeLevelValue, target: LlvmVariable): List<LlvmInstruction> {
        val fieldPointerVariable = irBuilder.generateLocal("fieldPointer")

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

    private fun fieldPointer(target: LlvmOperandLocal, receiver: LlvmOperand, receiverType: TypeLevelValue, fieldName: Identifier): LlvmGetElementPtr {
        val compiledObjectType = compiledType(objectType = receiverType)
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledObjectType.llvmPointerType(),
            pointer = receiver,
            indices = listOf(LlvmIndex(LlvmTypes.i32, LlvmOperandInt(0))) + compiledObjectType.getElementPtrIndices(fieldName = fieldName)
        )
    }

    internal fun tagValueAccess(target: LlvmVariable, operand: LlvmOperand): List<LlvmInstruction> {
        val objectPointer = irBuilder.generateLocal("objectPointer")
        val tagValuePointer = irBuilder.generateLocal("tagValuePointer")

        return listOf(
            LlvmIntToPtr(
                target = objectPointer,
                sourceType = compiledValueType,
                value = operand,
                targetType = CompiledUnionType.llvmPointerType()
            ),
            tagValuePointer(
                target = tagValuePointer,
                source = objectPointer,
                sourceType = CompiledUnionType.llvmPointerType()
            ),
            LlvmLoad(
                target = target,
                type = compiledTagValueType,
                pointer = tagValuePointer
            )
        )
    }

    private fun tagValuePointer(
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
