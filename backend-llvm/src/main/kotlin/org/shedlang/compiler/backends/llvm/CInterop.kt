package org.shedlang.compiler.backends.llvm

internal object CTypes {
    val int = LlvmTypes.i32
    val ssize_t = LlvmTypes.i64
    val size_t = LlvmTypes.i64
    val stringPointer = LlvmTypes.pointer(LlvmTypes.i8)
    val voidPointer = LlvmTypes.pointer(LlvmTypes.i8)
}

internal fun compileWrite(fd: LlvmOperand, buf: LlvmOperand, count: LlvmOperand): LlvmCall {
    // TODO: handle number of bytes written less than count
    return LlvmCall(
        target = null,
        returnType = CTypes.ssize_t,
        functionPointer = LlvmOperandGlobal("write"),
        arguments = listOf(
            LlvmTypedOperand(CTypes.int, fd),
            LlvmTypedOperand(CTypes.voidPointer, buf),
            LlvmTypedOperand(CTypes.size_t, count)
        )
    )
}
