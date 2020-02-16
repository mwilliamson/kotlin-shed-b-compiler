package org.shedlang.compiler.backends.llvm

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
        val newSize = LlvmOperandLocal(generateName("newSize"))
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
                ),
                LlvmAdd(
                    target = newSize,
                    type = LlvmTypes.i64,
                    left = newDataSize,
                    right = LlvmOperandInt(compiledStringLengthTypeSize)
                )
            ),
            allocString(target = newString, size = newSize),
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
                LlvmCall(
                    target = null,
                    returnType = LlvmTypes.pointer(LlvmTypes.i8),
                    functionPointer = LlvmOperandGlobal("memcpy"),
                    arguments = listOf(
                        LlvmTypedOperand(CTypes.stringPointer, newStringLeftStart),
                        LlvmTypedOperand(CTypes.stringPointer, leftStringDataStart),
                        LlvmTypedOperand(CTypes.size_t, leftSize)
                    )
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
                LlvmCall(
                    target = null,
                    returnType = LlvmTypes.pointer(LlvmTypes.i8),
                    functionPointer = LlvmOperandGlobal("memcpy"),
                    arguments = listOf(
                        LlvmTypedOperand(CTypes.stringPointer, newStringRightStart),
                        LlvmTypedOperand(CTypes.stringPointer, rightStringDataStart),
                        LlvmTypedOperand(CTypes.size_t, rightSize)
                    )
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

    private fun allocString(target: LlvmOperandLocal, size: LlvmOperand): List<LlvmInstruction> {
        return libc.typedMalloc(target, size, compiledStringType(0))
    }

    private fun storeStringDataSize(string: LlvmOperand, size: LlvmOperand): List<LlvmInstruction> {
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
            differentSizeValue = 0,
            memcmpConditionCode = LlvmIcmp.ConditionCode.EQ,
            context = context
        )
    }

    internal fun compileStringNotEqual(context: FunctionContext): FunctionContext {
        return compileStringComparison(
            differentSizeValue = 1,
            memcmpConditionCode = LlvmIcmp.ConditionCode.NE,
            context = context
        )
    }

    private fun compileStringComparison(
        differentSizeValue: Int,
        memcmpConditionCode: LlvmIcmp.ConditionCode,
        context: FunctionContext
    ): FunctionContext {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val result = LlvmOperandLocal(generateName("op"))

        val leftString = LlvmOperandLocal(generateName("left"))
        val rightString = LlvmOperandLocal(generateName("right"))
        val leftSize = LlvmOperandLocal(generateName("leftSize"))
        val rightSize = LlvmOperandLocal(generateName("rightSize"))
        val sameSize = LlvmOperandLocal(generateName("sameSize"))
        val resultPointer = LlvmOperandLocal(generateName("resultPointer"))
        val differentSizeLabel = generateName("differentSize")
        val compareBytesLabel = generateName("compareBytes")
        val leftBytesPointer = LlvmOperandLocal(generateName("leftBytesPointer"))
        val rightBytesPointer = LlvmOperandLocal(generateName("rightBytesPointer"))
        val memcmpResult = LlvmOperandLocal(generateName("memcmpResult"))
        val sameBytes = LlvmOperandLocal(generateName("sameBytes"))
        val endLabel = generateName("end")
        val resultBool = LlvmOperandLocal(generateName("resultBool"))

        return context3.addInstructions(listOf(
            listOf(
                rawValueToString(target = leftString, source = left),
                rawValueToString(target = rightString, source = right)
            ),
            stringSize(target = leftSize, source = leftString),
            stringSize(target = rightSize, source = rightString),
            listOf(
                LlvmAlloca(target = resultPointer, type = LlvmTypes.i1),
                LlvmIcmp(
                    target = sameSize,
                    conditionCode = LlvmIcmp.ConditionCode.EQ,
                    type = compiledStringLengthType,
                    left = leftSize,
                    right = rightSize
                ),
                LlvmBr(
                    condition = sameSize,
                    ifFalse = differentSizeLabel,
                    ifTrue = compareBytesLabel
                ),
                LlvmLabel(differentSizeLabel),
                LlvmStore(
                    type = LlvmTypes.i1,
                    value = LlvmOperandInt(differentSizeValue),
                    pointer = resultPointer
                ),
                LlvmBrUnconditional(endLabel),
                LlvmLabel(compareBytesLabel),
                stringDataStart(target = leftBytesPointer, source = leftString),
                stringDataStart(target = rightBytesPointer, source = rightString),
                LlvmCall(
                    target = memcmpResult,
                    returnType = CTypes.int,
                    functionPointer = LlvmOperandGlobal("memcmp"),
                    arguments = listOf(
                        LlvmTypedOperand(CTypes.voidPointer, leftBytesPointer),
                        LlvmTypedOperand(CTypes.voidPointer, rightBytesPointer),
                        LlvmTypedOperand(CTypes.size_t, leftSize)
                    )
                ),
                LlvmIcmp(
                    target = sameBytes,
                    conditionCode = memcmpConditionCode,
                    type = CTypes.int,
                    left = memcmpResult,
                    right = LlvmOperandInt(0)
                ),
                LlvmStore(
                    type = LlvmTypes.i1,
                    value = sameBytes,
                    pointer = resultPointer
                ),
                LlvmBrUnconditional(endLabel),
                LlvmLabel(endLabel),
                LlvmLoad(
                    target = resultBool,
                    type = LlvmTypes.i1,
                    pointer = resultPointer
                ),
                LlvmZext(
                    target = result,
                    sourceType = LlvmTypes.i1,
                    operand = resultBool,
                    targetType = compiledValueType
                )
            )
        ).flatten()).pushTemporary(result)
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
}
