package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.backends.ShedRuntime
import org.shedlang.compiler.stackir.StringAdd

internal class StringCompiler(private val irBuilder: LlvmIrBuilder, private val libc: LibcCallCompiler) {
    internal fun defineString(globalName: String, value: String): LlvmGlobalDefinition {
        val bytes = value.toByteArray(Charsets.UTF_8)

        val stringDataType = LlvmTypes.arrayType(bytes.size, LlvmTypes.i8)
        val stringValueType = LlvmTypes.structure(listOf(
            LlvmTypes.i64,
            stringDataType
        ))

        return LlvmGlobalDefinition(
            name = globalName,
            type = stringValueType,
            value = LlvmOperandStructure(listOf(
                LlvmTypedOperand(LlvmTypes.i64, LlvmOperandInt(bytes.size)),
                LlvmTypedOperand(
                    stringDataType,
                    LlvmOperandArray(bytes.map { byte ->
                        LlvmTypedOperand(LlvmTypes.i8, LlvmOperandInt(byte.toInt()))
                    })
                )
            )),
            unnamedAddr = true,
            isConstant = true
        )
    }

    internal fun operandRaw(definition: LlvmGlobalDefinition): LlvmOperandPtrToInt {
        return LlvmOperandPtrToInt(
            sourceType = LlvmTypes.pointer(definition.type),
            value = LlvmOperandGlobal(definition.name),
            targetType = compiledValueType
        )
    }

    internal fun compileStringAdd(
        instruction: StringAdd,
        context: FunctionContext
    ): FunctionContext {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val result = LlvmOperandLocal(generateName("op"))

        val leftString = LlvmOperandLocal(generateName("left"))
        val rightString = LlvmOperandLocal(generateName("right"))
        val leftSize = LlvmOperandLocal(generateName("leftSize"))
        val rightSize = LlvmOperandLocal(generateName("rightSize"))
        val leftStringDataStart = LlvmOperandLocal(generateName("leftStringDataStart"))
        val rightStringDataStart = LlvmOperandLocal(generateName("rightStringDataStart"))
        val newDataSize = LlvmOperandLocal(generateName("newDataSize"))
        val newString = LlvmOperandLocal(generateName("newString"))
        val newStringData = LlvmOperandLocal(generateName("newStringData"))
        val newStringLeftStart = LlvmOperandLocal(generateName("newStringLeftStart"))
        val newStringRightStart = LlvmOperandLocal(generateName("newStringRightStart"))

        return context3.addInstructions(listOf(
            listOf(
                rawValueToString(target = leftString, source = left),
                rawValueToString(target = rightString, source = right)
            ),
            stringSize(target = leftSize, source = leftString),
            stringSize(target = rightSize, source = rightString),
            listOf(
                LlvmAdd(
                    target = newDataSize,
                    type = compiledStringLengthType,
                    left = leftSize,
                    right = rightSize
                )
            ),
            allocString(target = newString, dataSize = newDataSize),
            storeStringDataSize(string = newString, size = newDataSize),
            listOf(
                stringData(
                    target = newStringData,
                    source = newString
                ),
                LlvmGetElementPtr(
                    target = newStringLeftStart,
                    pointerType = LlvmTypes.pointer(compiledStringDataType(0)),
                    pointer = newStringData,
                    indices = listOf(
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                    )
                ),
                stringDataStart(
                    target = leftStringDataStart,
                    source = leftString
                ),
                libc.memcpy(
                    target = null,
                    dest = newStringLeftStart,
                    src = leftStringDataStart,
                    n = leftSize
                ),
                LlvmGetElementPtr(
                    target = newStringRightStart,
                    pointerType = LlvmTypes.pointer(compiledStringDataType(0)),
                    pointer = newStringData,
                    indices = listOf(
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                        LlvmIndex(LlvmTypes.i64, leftSize)
                    )
                ),
                stringDataStart(
                    target = rightStringDataStart,
                    source = rightString
                ),
                libc.memcpy(
                    target = null,
                    dest = newStringRightStart,
                    src = rightStringDataStart,
                    n = rightSize
                ),
                LlvmPtrToInt(
                    target = result,
                    sourceType = compiledStringType(0),
                    value = newString,
                    targetType = compiledValueType
                )
            )
        ).flatten()).pushTemporary(result)
    }

    internal fun allocString(target: LlvmOperandLocal, dataSize: LlvmOperand): List<LlvmInstruction> {
        val size = LlvmOperandLocal(generateName("size"))
        return listOf(
            LlvmAdd(
                target = size,
                type = LlvmTypes.i64,
                left = dataSize,
                right = LlvmOperandInt(compiledStringLengthTypeSize)
            )
        ) + libc.typedMalloc(target, size, compiledStringType(0))
    }

    internal fun storeStringDataSize(string: LlvmOperand, size: LlvmOperand): List<LlvmInstruction> {
        val sizePointer = LlvmOperandLocal(generateName("sizePointer"))
        return listOf(
            stringSizePointer(
                target = sizePointer,
                source = string
            ),
            LlvmStore(
                type = compiledStringLengthType,
                value = size,
                pointer = sizePointer
            )
        )
    }

    internal fun compileStringEquals(context: FunctionContext): FunctionContext {
        return compileStringComparison(
            function = stringEqualsDeclaration,
            context = context,
        )
    }

    internal fun compileStringNotEqual(context: FunctionContext): FunctionContext {
        return compileStringComparison(
            function = stringNotEqualDeclaration,
            context = context
        )
    }

    private fun compileStringComparison(
        function: LlvmFunctionDeclaration,
        context: FunctionContext,
    ): FunctionContext {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val result = LlvmOperandLocal(generateName("op"))

        return context3.addInstruction(function.call(
            target = result,
            arguments = listOf(left, right),
        )).pushTemporary(result)
    }

    internal fun rawValueToString(target: LlvmOperandLocal, source: LlvmOperand): LlvmIntToPtr {
        return LlvmIntToPtr(
            target = target,
            sourceType = compiledValueType,
            value = source,
            targetType = compiledStringType(0)
        )
    }

    internal fun stringSize(target: LlvmOperandLocal, source: LlvmOperand): List<LlvmInstruction> {
        val sizePointer = LlvmOperandLocal(generateName("sizePointer"))
        return listOf(
            stringSizePointer(sizePointer, source),
            LlvmLoad(
                target = target,
                type = compiledStringLengthType,
                pointer = sizePointer
            )
        )
    }

    private fun stringSizePointer(target: LlvmOperandLocal, source: LlvmOperand): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledStringType(0),
            pointer = source,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i32, LlvmOperandInt(0))
            )
        )
    }

    internal fun stringData(target: LlvmOperandLocal, source: LlvmOperand): LlvmInstruction {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledStringType(0),
            pointer = source,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i32, LlvmOperandInt(1))
            )
        )
    }

    internal fun stringDataStart(target: LlvmOperandLocal, source: LlvmOperand): LlvmInstruction {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledStringType(0),
            pointer = source,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i32, LlvmOperandInt(1)),
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
            )
        )
    }

    private fun generateName(prefix: String) = irBuilder.generateName(prefix)

    private val stringEqualsDeclaration = LlvmFunctionDeclaration(
        name = ShedRuntime.stringEquals,
        callingConvention = LlvmCallingConvention.ccc,
        returnType = compiledValueType,
        parameters = listOf(
            LlvmParameter(compiledValueType, "left"),
            LlvmParameter(compiledValueType, "right"),
        )
    )

    private val stringNotEqualDeclaration = LlvmFunctionDeclaration(
        name = ShedRuntime.stringNotEqual,
        callingConvention = LlvmCallingConvention.ccc,
        returnType = compiledValueType,
        parameters = listOf(
            LlvmParameter(compiledValueType, "left"),
            LlvmParameter(compiledValueType, "right"),
        )
    )

    fun declarations(): List<LlvmTopLevelEntity> {
        return listOf(stringEqualsDeclaration, stringNotEqualDeclaration)
    }
}
