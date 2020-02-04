package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.Type
import java.nio.file.Path

internal class Compiler(private val image: Image, private val moduleSet: ModuleSet) {
    internal class Context(private val stack: PersistentList<LlvmOperand>) {
        fun pushTemporary(operand: LlvmOperand): Context {
            return Context(stack.add(operand))
        }

        fun popTemporary(): Pair<Context, LlvmOperand> {
            val (newStack, value) = stack.pop()
            return Pair(Context(newStack), value)
        }

        fun duplicateTemporary(): Context {
            return pushTemporary(peekTemporary())
        }

        fun discardTemporary(): Context {
            return Context(stack.removeAt(stack.lastIndex))
        }

        private fun peekTemporary() = stack.last()

        companion object {
            val EMPTY = Context(persistentListOf())
        }

        fun <T> result(value: T): CompilationResult<T> {
            return CompilationResult(
                value = value,
                moduleStatements = listOf(),
                context = this
            )
        }
    }

    fun compile(target: Path, mainModule: List<Identifier>) {
        val defineMainModule = moduleDefine(mainModule)

        val mainModuleVariable = LlvmOperandLocal("mainModule")
        val mainFunctionUntypedVariable = LlvmOperandLocal("mainFunctionUntyped")
        val mainFunctionVariable = LlvmOperandLocal("mainFunction")
        val exitCodeVariable = LlvmOperandLocal("exitCode")
        val main = LlvmFunctionDefinition(
            returnType = compiledValueType,
            name = "main",
            body = listOf(
                importModule(mainModule, target = mainModuleVariable),
                fieldAccess(
                    mainModuleVariable,
                    Identifier("main"),
                    receiverType = moduleSet.module(mainModule)!!.type,
                    target = mainFunctionUntypedVariable
                ),
                listOf(
                    LlvmIntToPtr(
                        target = mainFunctionVariable,
                        sourceType = compiledValueType,
                        value = mainFunctionUntypedVariable,
                        targetType = LlvmTypes.pointer(LlvmTypes.function(
                            returnType = compiledValueType,
                            parameterTypes = listOf()
                        ))
                    ),
                    LlvmCall(
                        target = exitCodeVariable,
                        returnType = compiledValueType,
                        functionPointer = mainFunctionVariable,
                        arguments = listOf()
                    ),
                    LlvmReturn(type = LlvmTypes.i64, value = exitCodeVariable)
                )
            ).flatten()
        )

        val module = LlvmModule(
            listOf(
                defineMainModule,
                listOf(main)
            ).flatten()
        )

        val source = serialiseProgram(module)

        println(withLineNumbers(source))

        target.toFile().writeText(source)
    }

    private fun moduleDefine(moduleName: List<Identifier>): List<LlvmModuleStatement> {
        return listOf(
            LlvmGlobalDefinition(
                name = nameForModuleValue(moduleName),
                type = compiledObjectType,
                value = LlvmNullPointer
            )
        ) + moduleInit(moduleName)
    }

    private fun moduleInit(moduleName: List<Identifier>): List<LlvmModuleStatement> {
        return compileInstructions(image.moduleInitialisation(moduleName), context = Context.EMPTY)
            .mapValue<LlvmModuleStatement> { instructions, _ ->
                LlvmFunctionDefinition(
                    name = nameForModuleInit(moduleName),
                    returnType = LlvmTypes.void,
                    body = instructions + listOf(LlvmReturnVoid)
                )
            }
            .toModuleStatements()
    }

    internal fun compileInstructions(instructions: List<Instruction>, context: Context): CompilationResult<List<LlvmInstruction>> {
        val localVariableIds = instructions
            .filterIsInstance<LocalStore>()
            .map { store -> store.variableId }
            .distinct()
        val allocateLocals = localVariableIds.map { localVariableId ->
            LlvmAlloca(target = variableForLocal(localVariableId), type = compiledValueType)
        }.toPersistentList<LlvmInstruction>()

        return instructions.fold(context.result(allocateLocals)) { result, instruction ->
            result.flatMapValue { previousInstructions, context ->
                compileInstruction(instruction, context = context).mapValue {
                    currentInstructions, _ -> previousInstructions.addAll(currentInstructions)
                }
            }
        }
    }

    private fun compileInstruction(instruction: Instruction, context: Context): CompilationResult<List<LlvmInstruction>> {
        when (instruction) {
            is BoolEquals -> {
                return compileBoolEquals(context)
            }

            is BoolNot -> {
                return compileBoolNot(context)
            }

            is BoolNotEqual -> {
                return compileBoolNotEqual(context)
            }

            is Call -> {
                val (context2, receiver) = context.popTemporary()
                val receiverPointer = LlvmOperandLocal(generateName("receiver"))
                val result = LlvmOperandLocal(generateName("result"))
                val context3 = context2.pushTemporary(result)

                return context3.result(listOf(
                    LlvmIntToPtr(
                        target = receiverPointer,
                        sourceType = compiledValueType,
                        value = receiver,
                        targetType = LlvmTypes.pointer(LlvmTypes.function(
                            returnType = compiledValueType,
                            parameterTypes = listOf()
                        ))
                    ),
                    LlvmCall(
                        target = result,
                        returnType = compiledValueType,
                        functionPointer = receiverPointer,
                        arguments = listOf()
                    )
                ))
            }

            is CodePointEquals -> {
                return compileCodePointComparison(LlvmIcmp.ConditionCode.EQ, context = context)
            }

            is CodePointNotEqual -> {
                return compileCodePointComparison(LlvmIcmp.ConditionCode.NE, context = context)
            }

            is CodePointGreaterThanOrEqual -> {
                return compileCodePointComparison(LlvmIcmp.ConditionCode.UGE, context = context)
            }

            is CodePointGreaterThan -> {
                return compileCodePointComparison(LlvmIcmp.ConditionCode.UGT, context = context)
            }

            is CodePointLessThanOrEqual -> {
                return compileCodePointComparison(LlvmIcmp.ConditionCode.ULE, context = context)
            }

            is CodePointLessThan -> {
                return compileCodePointComparison(LlvmIcmp.ConditionCode.ULT, context = context)
            }

            is DeclareFunction -> {
                val functionName = generateName("function")
                val functionPointerVariable = LlvmOperandLocal(generateName("functionPointer"))
                return compileInstructions(instruction.bodyInstructions, context = Context.EMPTY)
                    .flatMapValue { instructions, _ ->
                        val functionDefinition = LlvmFunctionDefinition(
                            name = functionName,
                            returnType = compiledValueType,
                            body = instructions
                        )

                        val context2 = context.pushTemporary(functionPointerVariable)

                        val getVariableAddress = LlvmPtrToInt(
                            target = functionPointerVariable,
                            targetType = compiledValueType,
                            value = LlvmOperandGlobal(functionName),
                            sourceType = LlvmTypes.pointer(LlvmTypes.function(
                                returnType = compiledValueType,
                                parameterTypes = listOf()
                            ))
                        )

                        context2.pushTemporary(functionPointerVariable)
                            .result(listOf<LlvmInstruction>(getVariableAddress))
                            .addModuleStatements(listOf(functionDefinition))
                    }
            }

            is DeclareShape -> {
                val constructorName = generateName("constructor")
                val constructorPointer = LlvmOperandLocal(generateName("constructorPointer"))
                val instanceBytes = LlvmOperandLocal(generateName("instanceBytes"))
                val instance = LlvmOperandLocal(generateName("instance"))
                val instanceAsValue = LlvmOperandLocal(generateName("instanceAsValue"))
                val tagValue = instruction.tagValue
                val shapeSize = if (tagValue == null) 0 else 1

                val extraModuleStatements = mutableListOf<LlvmModuleStatement>()

                val constructorDefinition = LlvmFunctionDefinition(
                    name = constructorName,
                    returnType = compiledValueType,
                    body = listOf(
                        listOf(
                            malloc(
                                target = instanceBytes,
                                bytes = LlvmOperandInt(compiledValueTypeSize * shapeSize)
                            ),
                            LlvmBitCast(
                                target = instance,
                                sourceType = LlvmTypes.pointer(LlvmTypes.i8),
                                value = instanceBytes,
                                targetType = compiledObjectType
                            )
                        ),

                        if (tagValue == null)
                            listOf()
                        else {
                            val tagValuePointer = LlvmOperandLocal(generateName("tagValuePointer"))

                            val tagValueDefinitionName = generateName("tagValueDefinition")

                            val (tagValueDefinition, tagValueOperand) = defineString(
                                globalName = tagValueDefinitionName,
                                value = tagValue.value.value
                            )
                            extraModuleStatements.add(tagValueDefinition)

                            listOf(
                                tagValuePointer(tagValuePointer, instance),
                                LlvmStore(
                                    type = compiledTagValueType,
                                    value = tagValueOperand,
                                    pointer = tagValuePointer
                                )
                            )
                        },

                        listOf(
                            LlvmPtrToInt(
                                target = instanceAsValue,
                                sourceType = compiledObjectType,
                                value = instance,
                                targetType = compiledValueType
                            ),
                            LlvmReturn(
                                type = compiledValueType,
                                value = instanceAsValue
                            )
                        )
                    ).flatten()
                )

                return context.pushTemporary(constructorPointer)
                    .result(listOf<LlvmInstruction>(
                        LlvmPtrToInt(
                            target = constructorPointer,
                            targetType = compiledValueType,
                            value = LlvmOperandGlobal(constructorName),
                            sourceType = LlvmTypes.pointer(LlvmTypes.function(
                                returnType = compiledValueType,
                                parameterTypes = listOf()
                            ))
                        )
                    ))
                    .addModuleStatements(listOf(constructorDefinition))
                    .addModuleStatements(extraModuleStatements)
            }

            is Discard -> {
                return context.discardTemporary().result(listOf())
            }

            is Duplicate -> {
                return context.duplicateTemporary().result(listOf())
            }

            is Exit -> {
                return context.result(listOf())
            }

            is IntAdd -> {
                return compileIntClosedOperation(::LlvmAdd, context = context)
            }

            is IntEquals -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.EQ, context = context)
            }

            is IntMinus -> {
                val result = LlvmOperandLocal(generateName("result"))

                val (context2, operand) = context.popTemporary()
                val context3 = context2.pushTemporary(result)

                return context3.result(listOf(
                    LlvmSub(
                        target = result,
                        type = compiledIntType,
                        left = LlvmOperandInt(0),
                        right = operand
                    )
                ))
            }

            is IntMultiply -> {
                return compileIntClosedOperation(::LlvmMul, context = context)
            }

            is IntNotEqual -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.NE, context = context)
            }

            is IntSubtract -> {
                return compileIntClosedOperation(::LlvmSub, context = context)
            }

            is Jump -> {
                return context.result(listOf(
                    LlvmBrUnconditional(labelToLlvmLabel(instruction.label))
                ))
            }

            is JumpIfFalse -> {
                val (context2, condition) = context.popTemporary()
                val trueLabel = createLlvmLabel("true")

                return context2.result(listOf(
                    LlvmBr(
                        condition = condition,
                        ifTrue = trueLabel,
                        ifFalse = labelToLlvmLabel(instruction.label)
                    ),
                    LlvmLabel(trueLabel)
                ))
            }

            is JumpIfTrue -> {
                val (context2, condition) = context.popTemporary()
                val falseLabel = createLlvmLabel("false")

                return context2.result(listOf(
                    LlvmBr(
                        condition = condition,
                        ifTrue = labelToLlvmLabel(instruction.label),
                        ifFalse = falseLabel
                    ),
                    LlvmLabel(falseLabel)
                ))
            }

            is Label -> {
                return context.result(listOf(
                    LlvmLabel(labelToLlvmLabel(instruction.value))
                ))
            }

            is LocalLoad -> {
                val value = LlvmOperandLocal(generateName("load"))
                val context2 = context.pushTemporary(value)
                return context2.result(listOf(
                    LlvmLoad(
                        target = value,
                        type = compiledValueType,
                        pointer = variableForLocal(instruction.variableId)
                    )
                ))
            }

            is LocalStore -> {
                val (context2, operand) = context.popTemporary()
                return context2.result(listOf(
                    LlvmStore(
                        type = compiledValueType,
                        value = operand,
                        pointer = variableForLocal(instruction.variableId)
                    )
                ))
            }

            is ModuleStore -> {
                val moduleVariableUntyped = LlvmOperandLocal(generateName("moduleUntyped"))
                val moduleVariable = LlvmOperandLocal(generateName("module"))
                val fieldPointerVariable = LlvmOperandLocal(generateName("fieldPointer"))
                val fieldValueVariable = LlvmOperandLocal(generateName("fieldValue"))
                val (exportName, exportVariableId) = instruction.exports.single()
                return context.result(listOf(
                    malloc(moduleVariableUntyped, LlvmOperandInt(compiledValueTypeSize * instruction.exports.size)),
                    LlvmBitCast(
                        target = moduleVariable,
                        sourceType = LlvmTypes.pointer(LlvmTypes.i8),
                        value = moduleVariableUntyped,
                        targetType = compiledObjectType
                    ),
                    // TODO: don't assume exactly one export
                    LlvmGetElementPtr(
                        target = fieldPointerVariable,
                        type = compiledObjectType.type,
                        pointer = moduleVariable,
                        indices = listOf(
                            LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                            LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                        )
                    ),
                    LlvmLoad(
                        target = fieldValueVariable,
                        type = compiledValueType,
                        pointer = variableForLocal(exportVariableId)
                    ),
                    LlvmStore(
                        type = compiledValueType,
                        value = fieldValueVariable,
                        pointer = fieldPointerVariable
                    ),
                    LlvmStore(
                        type = compiledObjectType,
                        value = moduleVariable,
                        pointer = operandForModuleValue(instruction.moduleName)
                    )
                ))
            }

            is PushValue -> {
                return stackValueToLlvmOperand(instruction.value, generateName = ::generateName, context = context)
                    .flatMapValue { operand, context2 ->
                        context2.pushTemporary(operand).result(listOf<LlvmInstruction>())
                    }
            }

            is Return -> {
                val (context2, returnVariable) = context.popTemporary()

                return context2.result(listOf(
                    LlvmReturn(type = compiledValueType, value = returnVariable)
                ))
            }

            is StringAdd -> {
                return compileStringAdd(instruction, context = context)
            }

            is StringEquals -> {
                return compileStringComparison(
                    differentSizeValue = 0,
                    memcmpConditionCode = LlvmIcmp.ConditionCode.EQ,
                    context = context
                )
            }

            is StringNotEqual -> {
                return compileStringComparison(
                    differentSizeValue = 1,
                    memcmpConditionCode = LlvmIcmp.ConditionCode.NE,
                    context = context
                )
            }

            is TagValueAccess -> {
                val (context2, operand) = context.popTemporary()
                val objectPointer = LlvmOperandLocal(generateName("objectPointer"))
                val tagValuePointer = LlvmOperandLocal(generateName("tagValuePointer"))
                val tagValue = LlvmOperandLocal(generateName("tagValue"))
                val context3 = context2.pushTemporary(tagValue)

                return context3.result(listOf(
                    LlvmIntToPtr(
                        target = objectPointer,
                        sourceType = compiledValueType,
                        value = operand,
                        targetType = compiledObjectType
                    ),
                    tagValuePointer(
                        target = tagValuePointer,
                        source = objectPointer
                    ),
                    LlvmLoad(
                        target = tagValue,
                        type = compiledValueType,
                        pointer = tagValuePointer
                    )
                ))
            }

            else -> {
                throw UnsupportedOperationException(instruction.toString())
            }
        }
    }

    private fun compileBoolNot(context: Context): CompilationResult<List<LlvmInstruction>> {
        val (context2, operand) = context.popTemporary()
        val booleanResult = LlvmOperandLocal(generateName("not_i1"))
        val fullResult = LlvmOperandLocal(generateName("not"))

        val context3 = context2.pushTemporary(fullResult)

        return context3.result(listOf(
            LlvmIcmp(
                target = booleanResult,
                conditionCode = LlvmIcmp.ConditionCode.EQ,
                type = compiledBoolType,
                left = operand,
                right = LlvmOperandInt(0)
            ),
            extendBool(target = fullResult, source = booleanResult)
        ))
    }

    private fun compileBoolEquals(context: Context): CompilationResult<List<LlvmInstruction>> {
        return compileBoolComparison(LlvmIcmp.ConditionCode.EQ, context = context)
    }

    private fun compileBoolNotEqual(context: Context): CompilationResult<List<LlvmInstruction>> {
        return compileBoolComparison(LlvmIcmp.ConditionCode.NE, context = context)
    }

    private fun compileBoolComparison(
        conditionCode: LlvmIcmp.ConditionCode,
        context: Context
    ): CompilationResult<List<LlvmInstruction>> {
        return compileComparisonOperation(
            conditionCode = conditionCode,
            context = context,
            operandType = compiledBoolType
        )
    }

    private fun compileCodePointComparison(
        conditionCode: LlvmIcmp.ConditionCode,
        context: Context
    ): CompilationResult<List<LlvmInstruction>> {
        return compileComparisonOperation(
            conditionCode = conditionCode,
            context = context,
            operandType = compiledCodePointType
        )
    }

    private fun compileIntClosedOperation(
        func: (LlvmVariable, LlvmType, LlvmOperand, LlvmOperand) -> LlvmInstruction,
        context: Context
    ): CompilationResult<List<LlvmInstruction>> {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val result = LlvmOperandLocal(generateName("op"))

        val context4 = context3.pushTemporary(result)

        return context4.result(listOf(
            func(result, compiledIntType, left, right)
        ))
    }

    private fun compileIntComparison(
        conditionCode: LlvmIcmp.ConditionCode,
        context: Context
    ): CompilationResult<List<LlvmInstruction>> {
        return compileComparisonOperation(
            conditionCode = conditionCode,
            context = context,
            operandType = compiledIntType
        )
    }

    private fun compileComparisonOperation(
        conditionCode: LlvmIcmp.ConditionCode,
        context: Context,
        operandType: LlvmType
    ): CompilationResult<List<LlvmInstruction>> {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val booleanResult = LlvmOperandLocal(generateName("op_i1"))
        val fullResult = LlvmOperandLocal(generateName("op"))

        val context4 = context3.pushTemporary(fullResult)

        return context4.result(listOf(
            LlvmIcmp(
                target = booleanResult,
                conditionCode = conditionCode,
                type = operandType,
                left = left,
                right = right
            ),
            extendBool(target = fullResult, source = booleanResult)
        ))
    }

    private fun extendBool(target: LlvmOperandLocal, source: LlvmOperandLocal): LlvmZext {
        return LlvmZext(
            target = target,
            sourceType = LlvmTypes.i1,
            operand = source,
            targetType = compiledBoolType
        )
    }

    private fun compileStringAdd(
        instruction: StringAdd,
        context: Context
    ): CompilationResult<List<LlvmInstruction>> {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val result = LlvmOperandLocal(generateName("op"))
        val context4 = context3.pushTemporary(result)

        val leftString = LlvmOperandLocal(generateName("left"))
        val rightString = LlvmOperandLocal(generateName("right"))
        val leftSize = LlvmOperandLocal(generateName("leftSize"))
        val rightSize = LlvmOperandLocal(generateName("rightSize"))
        val leftStringDataStart = LlvmOperandLocal(generateName("leftStringDataStart"))
        val rightStringDataStart = LlvmOperandLocal(generateName("rightStringDataStart"))
        val newDataSize = LlvmOperandLocal(generateName("newDataSize"))
        val newSize = LlvmOperandLocal(generateName("newSize"))
        val rawResult = LlvmOperandLocal(generateName("rawResult"))
        val newString = LlvmOperandLocal(generateName("newString"))
        val newSizePointer = LlvmOperandLocal(generateName("newSizePointer"))
        val newStringData = LlvmOperandLocal(generateName("newStringData"))
        val newStringLeftStart = LlvmOperandLocal(generateName("newStringLeftStart"))
        val newStringLeftStartAsInt = LlvmOperandLocal(generateName("newStringLeftStartAsInt"))
        val newStringRightStartAsInt = LlvmOperandLocal(generateName("newStringRightStartAsInt"))
        val newStringRightStart = LlvmOperandLocal(generateName("newStringRightStart"))

        return context4.result(listOf(
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
                ),
                malloc(rawResult, newSize),
                LlvmBitCast(
                    target = newString,
                    sourceType = LlvmTypes.pointer(LlvmTypes.i8),
                    value = rawResult,
                    targetType = compiledStringType(0)
                ),
                stringSizePointer(
                    target = newSizePointer,
                    source = newString
                ),
                LlvmStore(
                    type = compiledStringLengthType,
                    value = newDataSize,
                    pointer = newSizePointer
                ),
                stringData(
                    target = newStringData,
                    source = newString
                ),
                LlvmGetElementPtr(
                    target = newStringLeftStart,
                    type = compiledStringDataType(0),
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
                    type = compiledStringDataType(0),
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
        ).flatten())
    }

    private fun compileStringComparison(
        differentSizeValue: Int,
        memcmpConditionCode: LlvmIcmp.ConditionCode,
        context: Context
    ): CompilationResult<List<LlvmInstruction>> {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val result = LlvmOperandLocal(generateName("op"))
        val context4 = context3.pushTemporary(result)

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

        return context4.result(listOf(
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
        ).flatten())
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
            type = compiledStringValueType(0),
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
            type = compiledStringValueType(0),
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
            type = compiledStringValueType(0),
            pointer = source,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i32, LlvmOperandInt(1)),
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
            )
        )
    }

    private fun tagValuePointer(target: LlvmOperandLocal, source: LlvmOperandLocal): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            type = compiledObjectType.type,
            pointer = source,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
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

    private fun variableForLocal(variableId: Int): LlvmVariable {
        return LlvmOperandLocal("local_$variableId")
    }

    private fun fieldAccess(receiver: LlvmOperand, fieldName: Identifier, receiverType: Type, target: LlvmVariable): List<LlvmInstruction> {
        val fieldPointerVariable = LlvmOperandLocal("fieldPointer")

        // TODO: calculate fieldIndex
        val fieldIndex = 0

        return listOf(
            LlvmGetElementPtr(
                target = fieldPointerVariable,
                type = compiledObjectType.type,
                pointer = receiver,
                indices = listOf(
                    LlvmIndex(LlvmTypes.i32, LlvmOperandInt(0)),
                    LlvmIndex(LlvmTypes.i32, LlvmOperandInt(fieldIndex))
                )
            ),
            LlvmLoad(
                target = target,
                type = compiledValueType,
                pointer = fieldPointerVariable
            )
        )
    }

    private fun importModule(moduleName: List<Identifier>, target: LlvmVariable): List<LlvmInstruction> {
        return listOf(
            LlvmCall(
                target = null,
                returnType = LlvmTypes.void,
                functionPointer = operandForModuleInit(moduleName),
                arguments = listOf()
            ),
            LlvmLoad(
                target = target,
                type = compiledObjectType,
                pointer = operandForModuleValue(moduleName)
            )
        )
    }

    private fun operandForModuleInit(moduleName: List<Identifier>): LlvmOperand {
        return LlvmOperandGlobal(nameForModuleInit(moduleName))
    }

    private fun operandForModuleValue(moduleName: List<Identifier>): LlvmVariable {
        return LlvmOperandGlobal(nameForModuleValue(moduleName))
    }

    private fun nameForModuleInit(moduleName: List<Identifier>): String {
        return "shed__module_init__${serialiseModuleName(moduleName)}"
    }

    private fun nameForModuleValue(moduleName: List<Identifier>): String {
        return "shed__module_value__${serialiseModuleName(moduleName)}"
    }

    private fun serialiseModuleName(moduleName: List<Identifier>) =
        moduleName.joinToString("_") { part -> part.value }

    var nextNameIndex = 1

    private fun generateName(prefix: String): String {
        return prefix + "_" + nextNameIndex++
    }

    var nextLabelIndex = 1

    private fun createLlvmLabel(prefix: String): String {
        return "label_generated_" + prefix + "_" + nextLabelIndex++
    }

    private fun labelToLlvmLabel(label: Int): String {
        return "label_" + label
    }
}

private fun <T> PersistentList<T>.pop() = Pair(removeAt(lastIndex), last())

internal val compiledValueType = LlvmTypes.i64
internal val compiledValueTypeSize = 8
internal val compiledBoolType = compiledValueType
internal val compiledCodePointType = compiledValueType
internal val compiledIntType = compiledValueType
private val compiledTagValueType = compiledValueType

internal val compiledStringLengthType = LlvmTypes.i64
internal val compiledStringLengthTypeSize = 8
internal fun compiledStringDataType(size: Int) = LlvmTypes.arrayType(size, LlvmTypes.i8)
internal fun compiledStringValueType(size: Int) = LlvmTypes.structure(listOf(
    compiledStringLengthType,
    compiledStringDataType(size)
))
internal fun compiledStringType(size: Int) = LlvmTypes.pointer(compiledStringValueType(size))
private val compiledObjectType = LlvmTypes.pointer(LlvmTypes.arrayType(size = 0, elementType = compiledValueType))

internal object CTypes {
    val int = LlvmTypes.i32
    val ssize_t = LlvmTypes.i64
    val size_t = LlvmTypes.i64
    val stringPointer = LlvmTypes.pointer(LlvmTypes.i8)
    val voidPointer = LlvmTypes.pointer(LlvmTypes.i8)
}

internal fun stackValueToLlvmOperand(
    value: IrValue,
    generateName: (String) -> String,
    context: Compiler.Context
): CompilationResult<LlvmOperand> {
    return when (value) {
        is IrBool ->
            context.result(LlvmOperandInt(if (value.value) 1 else 0))

        is IrCodePoint ->
            context.result(LlvmOperandInt(value.value))

        is IrInt ->
            context.result(LlvmOperandInt(value.value.intValueExact()))

        is IrString -> {
            val globalName = generateName("string")

            val (stringDefinition, operand) = defineString(
                globalName = globalName,
                value = value.value
            )

            context.result(operand).addModuleStatements(listOf(stringDefinition))
        }

        is IrUnit ->
            context.result(LlvmOperandInt(0))

        else ->
            throw UnsupportedOperationException(value.toString())
    }
}

internal fun defineString(globalName: String, value: String): Pair<LlvmGlobalDefinition, LlvmOperand> {
    val bytes = value.toByteArray(Charsets.UTF_8)

    val stringDataType = LlvmTypes.arrayType(bytes.size, LlvmTypes.i8)
    val stringValueType = LlvmTypes.structure(listOf(
        LlvmTypes.i64,
        stringDataType
    ))

    val operand: LlvmOperand = LlvmOperandPtrToInt(
        sourceType = LlvmTypes.pointer(stringValueType),
        value = LlvmOperandGlobal(globalName),
        targetType = compiledValueType
    )

    val definition = LlvmGlobalDefinition(
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

    return Pair(definition, operand)
}

internal class CompilationResult<out T>(
    val value: T,
    val moduleStatements: List<LlvmModuleStatement>,
    val context: Compiler.Context
) {
    fun <R> mapValue(func: (T, Compiler.Context) -> R): CompilationResult<R> {
        return CompilationResult(
            value = func(value, context),
            moduleStatements = moduleStatements,
            context = context
        )
    }

    fun <R> flatMapValue(func: (T, Compiler.Context) -> CompilationResult<R>): CompilationResult<R> {
        val result = func(value, context)
        return CompilationResult(
            value = result.value,
            moduleStatements = moduleStatements + result.moduleStatements,
            context = result.context
        )
    }

    fun addModuleStatements(moduleStatements: List<LlvmModuleStatement>): CompilationResult<T> {
        return CompilationResult(
            value = value,
            moduleStatements = this.moduleStatements + moduleStatements,
            context = context
        )
    }
}

internal fun CompilationResult<LlvmModuleStatement>.toModuleStatements(): List<LlvmModuleStatement> {
    return moduleStatements + listOf(value)
}

internal fun serialiseProgram(module: LlvmModule): String {
    // TODO: handle malloc declaration properly
    return """
        declare i8* @malloc(i64)
        declare i8* @memcpy(i8*, i8*, i64)
        declare i32 @memcmp(i8*, i8*, i64)
        declare i32 @printf(i8* noalias nocapture, ...)
        declare i64 @write(i32, i8*, i64)
    """.trimIndent() + module.serialise()
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

fun withLineNumbers(source: String): String {
    return source.lines().mapIndexed { index, line ->
        (index + 1).toString().padStart(3) + " " + line
    }.joinToString("\n")
}
