package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.*

internal val compiledValueType = LlvmTypes.i64
internal val compiledValueTypeSize = compiledValueType.byteSize
internal val compiledBoolType = compiledValueType
internal val compiledUnicodeScalarType = compiledValueType
internal val compiledIntType = compiledValueType
internal val compiledTagValueType = compiledValueType

internal val compiledStringLengthType = LlvmTypes.i64
internal val compiledStringLengthTypeSize = compiledStringLengthType.byteSize
internal fun compiledStringDataType(size: Int) = LlvmTypes.arrayType(size, LlvmTypes.i8)
internal fun compiledStringValueType(size: Int) = LlvmTypes.structure(listOf(
    compiledStringLengthType,
    compiledStringDataType(size)
))
internal fun compiledStringType(size: Int) = LlvmTypes.pointer(compiledStringValueType(size))

internal val compiledTupleType = LlvmTypes.pointer(LlvmTypes.arrayType(size = 0, elementType = compiledValueType))

internal val compiledClosureEnvironmentType = LlvmTypes.arrayType(0, compiledValueType)

internal val compiledClosureEnvironmentPointerType = LlvmTypes.pointer(compiledClosureEnvironmentType)

internal fun compiledClosureFunctionPointerType(parameterTypes: List<LlvmType>): LlvmTypePointer {
    return LlvmTypes.pointer(compiledClosureFunctionType(parameterTypes))
}

internal fun compiledClosureFunctionType(parameterTypes: List<LlvmType>): LlvmType {
    return LlvmTypes.function(
        returnType = compiledValueType,
        parameterTypes = listOf(compiledClosureEnvironmentPointerType) + parameterTypes
    )
}

internal fun compiledClosurePointerType(parameterTypes: List<LlvmType>): LlvmTypePointer {
    return LlvmTypes.pointer(compiledClosureType(parameterTypes))
}

internal fun compiledClosureType(parameterTypes: List<LlvmType>): LlvmTypeStructure {
    return LlvmTypes.structure(listOf(
        compiledClosureFunctionPointerType(parameterTypes),
        compiledClosureEnvironmentType
    ))
}

internal val compiledClosureFunctionPointerSize = 8

internal fun compiledClosureSize(freeVariableCount: Int) = compiledClosureFunctionPointerSize + compiledValueTypeSize * freeVariableCount

internal fun compiledType(objectType: Type): CompiledType {
    if (objectType is MetaType && rawType(objectType.type) is ShapeType) {
        val shapeType = rawType(objectType.type) as ShapeType
        return CompiledShapeType(
            parameterTypes = shapeType.fields.map { compiledValueType },
            compiledObjectType = CompiledObjectType(
                fieldTypes = listOf(
                    Identifier("fields") to objectType.fieldType(Identifier("fields"))!!
                ),
                tagValue = null
            )
        )
    } else {
        val rawType = rawType(objectType)
        return when (rawType) {
            is ModuleType ->
                CompiledObjectType(
                    fieldTypes = rawType.fields.entries.map { entry -> entry.key to entry.value },
                    tagValue = null
                )

            is ShapeType ->
                CompiledObjectType(
                    fieldTypes = rawType.fields.map { field -> field.key to field.value.type },
                    tagValue = rawType.tagValue
                )

            else ->
                throw UnsupportedOperationException("type was: ${rawType.shortDescription}")
        }
    }
}

internal interface CompiledType {
    fun llvmPointerType(): LlvmTypePointer {
        return LlvmTypes.pointer(llvmType())
    }

    fun llvmType(): LlvmTypeStructure

    fun byteSize(): Int {
        return llvmType().byteSize
    }

    val tagValue: TagValue?

    fun getFieldPointer(target: LlvmOperandLocal, receiver: LlvmOperand, fieldName: Identifier): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = llvmPointerType(),
            pointer = receiver,
            indices = listOf(LlvmIndex(LlvmTypes.i32, LlvmOperandInt(0))) + getElementPtrIndices(fieldName)
        )
    }

    fun getElementPtrIndices(fieldName: Identifier): List<LlvmIndex>
}

internal class CompiledShapeType(
    private val parameterTypes: List<LlvmType>,
    private val compiledObjectType: CompiledObjectType
): CompiledType {
    override fun llvmType(): LlvmTypeStructure {
        return LlvmTypes.structure(listOf(
            compiledClosureType(parameterTypes = parameterTypes),
            compiledObjectType.llvmType()
        ))
    }

    override fun getElementPtrIndices(fieldName: Identifier): List<LlvmIndex> {
        return listOf(LlvmIndex.i32(1)) + compiledObjectType.getElementPtrIndices(fieldName)
    }

    override val tagValue: TagValue?
        get() = null
}

internal class CompiledObjectType(
    override val tagValue: TagValue?,
    fieldTypes: Collection<Pair<Identifier, Type>>
): CompiledType {
    private val sortedFieldTypes = fieldTypes.sortedBy { (fieldName, _) -> fieldName }
    private val sortedFieldNames = sortedFieldTypes.map { (fieldName, _) -> fieldName }

    override fun llvmType(): LlvmTypeStructure {
        val tagValueElementTypes = if (tagValue == null) listOf() else listOf(compiledTagValueType)
        val elementTypes = tagValueElementTypes + sortedFieldTypes.map { fieldType ->
            compiledValueType
        }
        return LlvmTypes.structure(elementTypes)
    }

    override fun getElementPtrIndices(fieldName: Identifier): List<LlvmIndex> {
        return listOf(
            LlvmIndex(LlvmTypes.i32, LlvmOperandInt(fieldIndex(fieldName)))
        )
    }

    private fun fieldIndex(fieldName: Identifier): Int {
        val fieldIndex = sortedFieldNames.indexOf(fieldName)

        return if (fieldIndex == -1) {
            // TODO: better exception
            throw Exception("field not found: ${fieldName.value}")
        } else {
            (if (tagValue == null) 0 else 1) + fieldIndex
        }
    }
}

internal object CompiledUnionType {
    internal fun llvmPointerType(): LlvmTypePointer {
        return LlvmTypes.pointer(llvmType())
    }

    internal fun llvmType(): LlvmType {
        return LlvmTypes.structure(elementTypes = listOf(compiledTagValueType))
    }
}
