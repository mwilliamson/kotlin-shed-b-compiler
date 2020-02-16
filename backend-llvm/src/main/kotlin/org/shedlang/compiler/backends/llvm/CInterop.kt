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

    private val mallocDeclaration = LlvmFunctionDeclaration(
        name = "malloc",
        returnType = CTypes.voidPointer,
        parameters = listOf(
            LlvmParameter(CTypes.size_t, "size")
        )
    )

    private fun malloc(target: LlvmOperandLocal, bytes: LlvmOperand): LlvmCall {
        return LlvmCall(
            target = target,
            returnType = CTypes.voidPointer,
            functionPointer = LlvmOperandGlobal("malloc"),
            arguments = listOf(
                LlvmTypedOperand(CTypes.size_t, bytes)
            )
        )
    }

    private val memcmpDeclaration = LlvmFunctionDeclaration(
        name = "memcmp",
        returnType = CTypes.int,
        parameters = listOf(
            LlvmParameter(CTypes.voidPointer, "s1"),
            LlvmParameter(CTypes.voidPointer, "s2"),
            LlvmParameter(CTypes.size_t, "n")
        )
    )

    internal fun memcmp(target: LlvmOperandLocal, s1: LlvmOperand, s2: LlvmOperand, n: LlvmOperand): LlvmCall {
        return LlvmCall(
            target = target,
            returnType = CTypes.int,
            functionPointer = LlvmOperandGlobal("memcmp"),
            arguments = listOf(
                LlvmTypedOperand(CTypes.voidPointer, s1),
                LlvmTypedOperand(CTypes.voidPointer, s2),
                LlvmTypedOperand(CTypes.size_t, n)
            )
        )
    }

    private val memcpyDeclaration = LlvmFunctionDeclaration(
        name = "memcpy",
        returnType = CTypes.voidPointer,
        parameters = listOf(
            LlvmParameter(CTypes.voidPointer, "dest"),
            LlvmParameter(CTypes.voidPointer, "src"),
            LlvmParameter(CTypes.size_t, "n")
        )
    )

    internal fun memcpy(target: LlvmOperandLocal?, dest: LlvmOperand, src: LlvmOperand, n: LlvmOperand): LlvmCall {
        return LlvmCall(
            target = target,
            returnType = CTypes.voidPointer,
            functionPointer = LlvmOperandGlobal("memcpy"),
            arguments = listOf(
                LlvmTypedOperand(CTypes.voidPointer, dest),
                LlvmTypedOperand(CTypes.voidPointer, src),
                LlvmTypedOperand(CTypes.size_t, n)
            )
        )
    }

    private val printfDeclaration = LlvmFunctionDeclaration(
        name = "printf",
        returnType = CTypes.int,
        parameters = listOf(
            LlvmParameter(CTypes.stringPointer, "format") // TODO: noalias nocapture
        ),
        hasVarargs = true
    )

    internal fun printf(target: LlvmOperandLocal?, format: LlvmOperand, args: List<LlvmTypedOperand>): LlvmCall {
        return LlvmCall(
            target = target,
            returnType = printfDeclaration.type(),
            functionPointer = LlvmOperandGlobal("printf"),
            arguments = listOf(
                LlvmTypedOperand(CTypes.stringPointer, format)
            ) + args
        )
    }

    private val snprintfDeclaration = LlvmFunctionDeclaration(
        name = "snprintf",
        returnType = CTypes.int,
        parameters = listOf(
            LlvmParameter(CTypes.stringPointer, "str"),
            LlvmParameter(CTypes.size_t, "size"),
            LlvmParameter(CTypes.stringPointer, "format")
        ),
        hasVarargs = true
    )

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

    private val writeDeclaration = LlvmFunctionDeclaration(
        name = "write",
        returnType = CTypes.ssize_t,
        parameters = listOf(
            LlvmParameter(CTypes.int, "fd"),
            LlvmParameter(CTypes.voidPointer, "buf"),
            LlvmParameter(CTypes.size_t, "count")
        )
    )

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

    internal fun declarations(): List<LlvmFunctionDeclaration> {
        return listOf(
            mallocDeclaration,
            memcmpDeclaration,
            memcpyDeclaration,
            printfDeclaration,
            snprintfDeclaration,
            writeDeclaration
        )
    }
}
