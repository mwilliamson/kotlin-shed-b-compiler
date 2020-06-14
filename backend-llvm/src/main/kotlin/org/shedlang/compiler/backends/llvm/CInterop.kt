package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.findRoot

internal object CTypes {
    val char = LlvmTypes.i8
    val int = LlvmTypes.i32
    val ssize_t = LlvmTypes.i64
    val size_t = LlvmTypes.i64
    val stringPointer = LlvmTypes.pointer(char)
    val voidPointer = LlvmTypes.pointer(LlvmTypes.i8)
    val void = LlvmTypes.void
    val jmpBuf by lazy {
        LlvmTypes.arrayType(
            size = findRoot().resolve("stdlib-llvm/sizeof_jmp_buf.txt").toFile().readText().trim().toInt(),
            elementType = LlvmTypes.i8
        )
    }
    val jmpBufPointer by lazy {
        LlvmTypes.pointer(jmpBuf)
    }
}

internal class LibcCallCompiler(private val irBuilder: LlvmIrBuilder) {
    internal fun typedMalloc(target: LlvmOperandLocal, bytes: Int, type: LlvmType): List<LlvmInstruction> {
        return typedMalloc(target, LlvmOperandInt(bytes), type)
    }

    internal fun typedMalloc(target: LlvmOperandLocal, bytes: LlvmOperand, type: LlvmType): List<LlvmInstruction> {
        val mallocResult = LlvmOperandLocal(irBuilder.generateName("bytes"))

        return listOf(
            malloc(target = mallocResult, size = bytes),
            LlvmBitCast(
                target = target,
                sourceType = LlvmTypes.pointer(LlvmTypes.i8),
                value = mallocResult,
                targetType = type
            )
        )
    }

    private val mallocDeclaration = LlvmFunctionDeclaration(
        name = "GC_malloc",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.voidPointer,
        parameters = listOf(
            LlvmParameter(CTypes.size_t, "size")
        )
    )

    private fun malloc(target: LlvmOperandLocal, size: LlvmOperand): LlvmCall {
        return call(mallocDeclaration, target = target, arguments = listOf(size))
    }

    private val memcmpDeclaration = LlvmFunctionDeclaration(
        name = "memcmp",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.int,
        parameters = listOf(
            LlvmParameter(CTypes.voidPointer, "s1"),
            LlvmParameter(CTypes.voidPointer, "s2"),
            LlvmParameter(CTypes.size_t, "n")
        )
    )

    internal fun memcmp(target: LlvmOperandLocal, s1: LlvmOperand, s2: LlvmOperand, n: LlvmOperand): LlvmCall {
        return call(memcmpDeclaration, target = target, arguments = listOf(s1, s2, n))
    }

    private val memcpyDeclaration = LlvmFunctionDeclaration(
        name = "memcpy",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.voidPointer,
        parameters = listOf(
            LlvmParameter(CTypes.voidPointer, "dest"),
            LlvmParameter(CTypes.voidPointer, "src"),
            LlvmParameter(CTypes.size_t, "n")
        )
    )

    internal fun memcpy(target: LlvmOperandLocal?, dest: LlvmOperand, src: LlvmOperand, n: LlvmOperand): LlvmCall {
        return call(memcpyDeclaration, target = target, arguments = listOf(dest, src, n))
    }

    private val printfDeclaration = LlvmFunctionDeclaration(
        name = "printf",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.int,
        parameters = listOf(
            LlvmParameter(CTypes.stringPointer, "format") // TODO: noalias nocapture
        ),
        hasVarargs = true
    )

    internal fun printf(target: LlvmOperandLocal?, format: LlvmOperand, args: List<LlvmTypedOperand>): LlvmCall {
        return call(printfDeclaration, target = null, arguments = listOf(format), varargs = args)
    }

    private val snprintfDeclaration = LlvmFunctionDeclaration(
        name = "snprintf",
        callingConvention = LlvmCallingConvention.ccc,
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
        return call(snprintfDeclaration, target = target, arguments = listOf(str, size, format), varargs = args)
    }

    private val writeDeclaration = LlvmFunctionDeclaration(
        name = "write",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = CTypes.ssize_t,
        parameters = listOf(
            LlvmParameter(CTypes.int, "fd"),
            LlvmParameter(CTypes.voidPointer, "buf"),
            LlvmParameter(CTypes.size_t, "count")
        )
    )

    internal fun write(fd: LlvmOperand, buf: LlvmOperand, count: LlvmOperand): LlvmCall {
        // TODO: handle number of bytes written less than count
        return call(writeDeclaration, target = null, arguments = listOf(fd, buf, count))
    }

    internal val setjmpReturnType = CTypes.int
    private val setjmpDeclaration = LlvmFunctionDeclaration(
        name = "setjmp",
        callingConvention = LlvmCallingConvention.ccc,
        returnType = setjmpReturnType,
        parameters = listOf(
            LlvmParameter(CTypes.jmpBufPointer, "env")
        )
    )

    internal fun setjmp(target: LlvmOperandLocal, env: LlvmOperand): LlvmCall {
        return call(setjmpDeclaration, target = target, arguments = listOf(env))
    }

    internal fun call(
        function: LlvmFunctionDeclaration,
        target: LlvmVariable?,
        arguments: List<LlvmOperand>,
        varargs: List<LlvmTypedOperand>? = null
    ): LlvmCall {
        return LlvmCall(
            target = target,
            callingConvention = function.callingConvention,
            returnType = if (varargs == null) function.returnType else function.type(),
            functionPointer = LlvmOperandGlobal(function.name),
            arguments = function.parameters.zip(arguments) { parameter, argument ->
                LlvmTypedOperand(parameter.type, argument)
            } + varargs.orEmpty()
        )
    }

    internal fun declarations(): List<LlvmFunctionDeclaration> {
        return listOf(
            mallocDeclaration,
            memcmpDeclaration,
            memcpyDeclaration,
            printfDeclaration,
            snprintfDeclaration,
            writeDeclaration,

            setjmpDeclaration
        )
    }
}
