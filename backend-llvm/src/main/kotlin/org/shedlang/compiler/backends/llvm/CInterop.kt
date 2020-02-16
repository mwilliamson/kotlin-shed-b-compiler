package org.shedlang.compiler.backends.llvm

internal object CTypes {
    val char = LlvmTypes.i8
    val int = LlvmTypes.i32
    val ssize_t = LlvmTypes.i64
    val size_t = LlvmTypes.i64
    val stringPointer = LlvmTypes.pointer(char)
    val voidPointer = LlvmTypes.pointer(LlvmTypes.i8)
}

internal class LibcCallCompiler(private val irBuilder: LlvmIrBuilder) {
    internal fun write(fd: LlvmOperand, buf: LlvmOperand, count: LlvmOperand): LlvmCall {
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

    internal fun typedMalloc(target: LlvmOperandLocal, bytes: Int, type: LlvmType): List<LlvmInstruction> {
        return typedMalloc(target, LlvmOperandInt(bytes), type)
    }

    internal fun typedMalloc(target: LlvmOperandLocal, bytes: LlvmOperand, type: LlvmType): List<LlvmInstruction> {
        val mallocResult = LlvmOperandLocal(irBuilder.generateName("bytes"))

        return listOf(
            malloc(target = mallocResult, bytes = bytes),
            LlvmBitCast(
                target = target,
                sourceType = LlvmTypes.pointer(LlvmTypes.i8),
                value = mallocResult,
                targetType = type
            )
        )
    }

    private fun malloc(target: LlvmOperandLocal, bytes: LlvmOperand): LlvmCall {
        return LlvmCall(
            target = target,
            returnType = LlvmTypes.pointer(LlvmTypes.i8),
            functionPointer = LlvmOperandGlobal("malloc"),
            arguments = listOf(
                LlvmTypedOperand(LlvmTypes.i64, bytes)
            )
        )
    }

    internal fun snprintf(
        target: LlvmVariable,
        str: LlvmOperand,
        size: LlvmOperand,
        format: LlvmOperand,
        args: List<LlvmTypedOperand>
    ): LlvmInstruction {
        return LlvmCall(
            target = target,
            returnType = LlvmTypes.function(
                returnType = CTypes.int,
                parameterTypes = listOf(
                    CTypes.stringPointer,
                    CTypes.size_t,
                    CTypes.stringPointer
                ),
                hasVarargs = true
            ),
            functionPointer = LlvmOperandGlobal("snprintf"),
            arguments = listOf(
                LlvmTypedOperand(CTypes.stringPointer, str),
                LlvmTypedOperand(CTypes.size_t, size),
                LlvmTypedOperand(CTypes.stringPointer, format)
            ) + args
        )
    }
}
