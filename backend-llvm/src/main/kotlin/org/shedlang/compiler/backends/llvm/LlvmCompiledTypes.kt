package org.shedlang.compiler.backends.llvm

internal val compiledValueType = LlvmTypes.i64
internal val compiledValueTypeSize = 8
internal val compiledBoolType = compiledValueType
internal val compiledCodePointType = compiledValueType
internal val compiledIntType = compiledValueType
internal val compiledTagValueType = compiledValueType

internal val compiledStringLengthType = LlvmTypes.i64
internal val compiledStringLengthTypeSize = 8
internal fun compiledStringDataType(size: Int) = LlvmTypes.arrayType(size, LlvmTypes.i8)
internal fun compiledStringValueType(size: Int) = LlvmTypes.structure(listOf(
    compiledStringLengthType,
    compiledStringDataType(size)
))
internal fun compiledStringType(size: Int) = LlvmTypes.pointer(compiledStringValueType(size))
internal fun compiledObjectType(size: Int = 0) = LlvmTypes.pointer(LlvmTypes.arrayType(size = size, elementType = compiledValueType))
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
