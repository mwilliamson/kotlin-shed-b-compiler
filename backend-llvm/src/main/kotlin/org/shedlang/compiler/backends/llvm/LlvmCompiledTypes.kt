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

internal val compiledTuplePointerType = LlvmTypes.pointer(compiledTupleType(elementCount = 0))
internal fun compiledTupleType(elementCount: Int) = LlvmTypes.arrayType(size = elementCount, elementType = compiledValueType)

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

internal fun compiledType(objectType: StaticValue): CompiledType {
    if (objectType is TypeAlias) {
        // TODO: better handling of type aliases
        return compiledType(objectType.aliasedType)
    } else if (objectType is StaticValueType && rawValue(objectType.value) is ShapeType) {
        val shapeType = rawValue(objectType.value) as ShapeType
        return CompiledShapeType(
            parameterTypes = shapeType.allFields.map { compiledValueType },
            compiledObjectType = CompiledObjectType(
                fieldTypes = listOf(
                    Identifier("fields") to objectType.fieldType(Identifier("fields"))!!
                ),
                tagValue = null
            )
        )
    } else if (objectType is StaticValueType && objectType.value is UserDefinedEffect) {
        val effect = objectType.value as UserDefinedEffect
        return CompiledObjectType(
            tagValue = null,
            fieldTypes = effect.operations.map { (operationName, operationType) ->
                operationName to operationType
            }
        )
    } else {
        val rawType = rawValue(objectType)
        return when (rawType) {
            is ModuleType ->
                CompiledObjectType(
                    fieldTypes = rawType.fields.entries.map { entry -> entry.key to entry.value },
                    tagValue = null
                )

            is ShapeType ->
                CompiledObjectType(
                    fieldTypes = rawType.allFields.map { field -> field.key to field.value.type },
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

    fun getElementPtrIndices(fieldName: Identifier): List<LlvmIndex>
}

internal class CompiledShapeType(
    private val parameterTypes: List<LlvmType>,
    private val compiledObjectType: CompiledObjectType
): CompiledType {
    override fun llvmType(): LlvmTypeStructure {
        return LlvmTypes.structure(listOf(
            compiledClosureType(parameterTypes = parameterTypes),
            compiledTagValueType,
            compiledObjectType.llvmType()
        ))
    }

    fun closureIndices(): List<LlvmIndex> {
        return listOf(LlvmIndex.i32(0))
    }

    fun typeTagValueIndices(): List<LlvmIndex> {
        return listOf(LlvmIndex.i32(1))
    }

    override fun getElementPtrIndices(fieldName: Identifier): List<LlvmIndex> {
        return listOf(LlvmIndex.i32(2)) + compiledObjectType.getElementPtrIndices(fieldName)
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

    fun cType(): CType {
        val members = sortedFieldNames.map { fieldName ->
            CNamedType("ShedValue") to fieldName.value
        }
        return CStruct(members = members)
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
