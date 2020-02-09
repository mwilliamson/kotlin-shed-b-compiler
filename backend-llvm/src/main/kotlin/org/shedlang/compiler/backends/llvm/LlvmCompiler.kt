package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.*
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.TagValue
import org.shedlang.compiler.types.Type
import java.nio.file.Path

internal class Compiler(private val image: Image, private val moduleSet: ModuleSet) {
    internal interface LabelPredecessor {
        val stack: PersistentList<LlvmOperand>
        val basicBlockName: String
    }

    internal class FunctionContext(
        override val stack: PersistentList<LlvmOperand>,
        internal val instructions: PersistentList<LlvmInstruction>,
        override val basicBlockName: String,
        internal val topLevelEntities: PersistentList<LlvmTopLevelEntity>,
        private val labelPredecessors: PersistentMultiMap<String, LabelPredecessor>,
        private val generateName: (String) -> String
    ): LabelPredecessor {
        fun addInstructions(vararg newInstructions: LlvmInstruction): FunctionContext {
            return addInstructions(newInstructions.asList())
        }

        fun addInstructions(newInstructions: List<LlvmInstruction>): FunctionContext {
            return newInstructions.fold(this) { acc, newInstruction ->
                acc.addInstruction(newInstruction)
            }
        }

        fun addInstruction(newInstruction: LlvmInstruction): FunctionContext {
            val newLabelPredecessors = when (newInstruction) {
                is LlvmBrUnconditional ->
                    labelPredecessors.add(newInstruction.label, this)
                is LlvmBr ->
                    labelPredecessors
                        .add(newInstruction.ifTrue, this)
                        .add(newInstruction.ifFalse, this)
                is LlvmLabel -> {
                    val previousInstruction = instructions.lastOrNull()
                    if (previousInstruction == null || isTerminator(previousInstruction)) {
                        labelPredecessors
                    } else {
                        labelPredecessors.add(newInstruction.name, this)
                    }
                }
                else ->
                    labelPredecessors
            }

            val newBasicBlockName = when (newInstruction) {
                is LlvmLabel ->
                    newInstruction.name
                else ->
                    basicBlockName
            }

            val (newStack, extraInstructions) = when (newInstruction) {
                is LlvmLabel ->
                    mergeStacks(newLabelPredecessors[newInstruction.name])
                else ->
                    Pair(stack, listOf())
            }

            return FunctionContext(
                stack = newStack,
                instructions = instructions.add(newInstruction).addAll(extraInstructions),
                basicBlockName = newBasicBlockName,
                topLevelEntities = topLevelEntities,
                labelPredecessors = newLabelPredecessors,
                generateName = generateName
            )
        }

        fun addTopLevelEntities(newTopLevelEntities: List<LlvmTopLevelEntity>): FunctionContext {
            return FunctionContext(
                stack = stack,
                instructions = instructions,
                basicBlockName = basicBlockName,
                topLevelEntities = topLevelEntities.addAll(newTopLevelEntities),
                labelPredecessors = labelPredecessors,
                generateName = generateName
            )
        }

        fun pushTemporary(operand: LlvmOperand): FunctionContext {
            return updateStack(stack.add(operand))
        }

        fun popTemporary(): Pair<FunctionContext, LlvmOperand> {
            val (newStack, value) = stack.pop()
            return Pair(updateStack(newStack), value)
        }

        fun duplicateTemporary(): FunctionContext {
            return pushTemporary(peekTemporary())
        }

        fun discardTemporary(): FunctionContext {
            return updateStack(stack.removeAt(stack.lastIndex))
        }

        private fun peekTemporary() = stack.last()

        private fun updateStack(newStack: PersistentList<LlvmOperand>): FunctionContext {
            return FunctionContext(
                stack = newStack,
                instructions = instructions,
                basicBlockName = basicBlockName,
                topLevelEntities = topLevelEntities,
                labelPredecessors = labelPredecessors,
                generateName = generateName
            )
        }

        private fun mergeStacks(predecessors: List<LabelPredecessor>): Pair<PersistentList<LlvmOperand>, List<LlvmInstruction>> {
            val stacks = predecessors.map { predecessor -> predecessor.stack }
            val stackSizes = stacks.distinctBy { stack -> stack.size }
            if (stackSizes.size == 0) {
                return Pair(persistentListOf(), listOf())
            } else if (stackSizes.size == 1) {
                val (newStack, mergeInstructions) = (0 until stackSizes.single().size).map { stackIndex ->
                    mergeOperands(predecessors, stackIndex)
                }.unzip()
                return Pair(newStack.toPersistentList(), mergeInstructions.flatten())
            } else {
                throw Exception("cannot merge stacks")
            }
        }

        private fun mergeOperands(predecessors: List<LabelPredecessor>, stackIndex: Int): Pair<LlvmOperand, List<LlvmInstruction>> {
            val distinctOperands = predecessors.map { predecessor -> predecessor.stack[stackIndex] }.distinct()
            if (distinctOperands.size == 1) {
                return Pair(distinctOperands.single(), listOf())
            } else {
                val mergedValue = LlvmOperandLocal(generateName("val"))
                val mergeInstruction = LlvmPhi(
                    target = mergedValue,
                    type = compiledValueType,
                    pairs = predecessors.map { predecessor ->
                        LlvmPhiPair(value = predecessor.stack[stackIndex], predecessorBasicBlockName = predecessor.basicBlockName)
                    }
                )
                return Pair(mergedValue, listOf(mergeInstruction))
            }
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

    private fun moduleDefine(moduleName: List<Identifier>): List<LlvmTopLevelEntity> {
        return listOf(
            LlvmGlobalDefinition(
                name = nameForModuleValue(moduleName),
                type = compiledObjectType,
                value = LlvmNullPointer
            )
        ) + moduleInit(moduleName)
    }

    private fun moduleInit(moduleName: List<Identifier>): List<LlvmTopLevelEntity> {
        val bodyContext = compileInstructions(
            image.moduleInitialisation(moduleName),
            context = startFunction()
        )

        return bodyContext.topLevelEntities.add(
            LlvmFunctionDefinition(
                name = nameForModuleInit(moduleName),
                returnType = LlvmTypes.void,
                body = bodyContext.instructions.add(LlvmReturnVoid)
            )
        )
    }

    internal fun compileInstructions(instructions: List<Instruction>, context: FunctionContext): FunctionContext {
        val localVariableIds = instructions
            .filterIsInstance<LocalStore>()
            .map { store -> store.variableId }
            .distinct()

        val allocateLocals = localVariableIds.map { localVariableId ->
            LlvmAlloca(target = variableForLocal(localVariableId), type = compiledValueType)
        }.toPersistentList<LlvmInstruction>()

        return instructions.fold(context.addInstructions(allocateLocals)) { result, instruction ->
            compileInstruction(instruction, context = result)
        }
    }

    private fun compileInstruction(instruction: Instruction, context: FunctionContext): FunctionContext {
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

                return context2.addInstructions(
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
                ).pushTemporary(result)
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

                val bodyContext = compileInstructions(
                    instruction.bodyInstructions,
                    context = startFunction()
                )

                val functionDefinition = LlvmFunctionDefinition(
                    name = functionName,
                    returnType = compiledValueType,
                    body = bodyContext.instructions
                )

                val getVariableAddress = LlvmPtrToInt(
                    target = functionPointerVariable,
                    targetType = compiledValueType,
                    value = LlvmOperandGlobal(functionName),
                    sourceType = LlvmTypes.pointer(LlvmTypes.function(
                        returnType = compiledValueType,
                        parameterTypes = listOf()
                    ))
                )

                return context
                    .addTopLevelEntities(bodyContext.topLevelEntities)
                    .addTopLevelEntities(listOf(functionDefinition))
                    .addInstructions(getVariableAddress)
                    .pushTemporary(functionPointerVariable)
            }

            is DeclareShape -> {
                val constructorName = generateName("constructor")
                val constructorPointer = LlvmOperandLocal(generateName("constructorPointer"))
                val instanceBytes = LlvmOperandLocal(generateName("instanceBytes"))
                val instance = LlvmOperandLocal(generateName("instance"))
                val instanceAsValue = LlvmOperandLocal(generateName("instanceAsValue"))
                val tagValue = instruction.tagValue
                val shapeSize = if (tagValue == null) 0 else 1

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

                        if (tagValue == null) {
                            listOf()
                        } else {
                            val tagValuePointer = LlvmOperandLocal(generateName("tagValuePointer"))

                            listOf(
                                tagValuePointer(tagValuePointer, instance),
                                LlvmStore(
                                    type = compiledTagValueType,
                                    value = LlvmOperandInt(tagValueToInt(tagValue)),
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

                return context
                    .addTopLevelEntities(listOf(constructorDefinition))
                    .addInstructions(
                        LlvmPtrToInt(
                            target = constructorPointer,
                            targetType = compiledValueType,
                            value = LlvmOperandGlobal(constructorName),
                            sourceType = LlvmTypes.pointer(LlvmTypes.function(
                                returnType = compiledValueType,
                                parameterTypes = listOf()
                            ))
                        )
                    )
                    .pushTemporary(constructorPointer)
            }

            is Discard -> {
                return context.discardTemporary()
            }

            is Duplicate -> {
                return context.duplicateTemporary()
            }

            is Exit -> {
                return context
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

                return context2.addInstructions(
                    LlvmSub(
                        target = result,
                        type = compiledIntType,
                        left = LlvmOperandInt(0),
                        right = operand
                    )
                ).pushTemporary(result)
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
                return context.addInstructions(
                    LlvmBrUnconditional(labelToLlvmLabel(instruction.label))
                )
            }

            is JumpIfFalse -> {
                val (context2, condition) = context.popTemporary()
                val conditionTruncated = LlvmOperandLocal(generateName("condition"))
                val trueLabel = createLlvmLabel("true")

                return context2.addInstructions(
                    LlvmTrunc(
                        target = conditionTruncated,
                        sourceType = compiledValueType,
                        operand = condition,
                        targetType = LlvmTypes.i1
                    ),
                    LlvmBr(
                        condition = conditionTruncated,
                        ifTrue = trueLabel,
                        ifFalse = labelToLlvmLabel(instruction.label)
                    ),
                    LlvmLabel(trueLabel)
                )
            }

            is JumpIfTrue -> {
                val (context2, condition) = context.popTemporary()
                val conditionTruncated = LlvmOperandLocal(generateName("condition"))
                val falseLabel = createLlvmLabel("false")

                return context2.addInstructions(
                    LlvmTrunc(
                        target = conditionTruncated,
                        sourceType = compiledValueType,
                        operand = condition,
                        targetType = LlvmTypes.i1
                    ),
                    LlvmBr(
                        condition = conditionTruncated,
                        ifTrue = labelToLlvmLabel(instruction.label),
                        ifFalse = falseLabel
                    ),
                    LlvmLabel(falseLabel)
                )
            }

            is Label -> {
                val label = labelToLlvmLabel(instruction.value)
                return context.addInstructions(LlvmLabel(label))
            }

            is LocalLoad -> {
                val value = LlvmOperandLocal(generateName("load"))
                return context.addInstructions(
                    LlvmLoad(
                        target = value,
                        type = compiledValueType,
                        pointer = variableForLocal(instruction.variableId)
                    )
                ).pushTemporary(value)
            }

            is LocalStore -> {
                val (context2, operand) = context.popTemporary()
                return context2.addInstructions(
                    LlvmStore(
                        type = compiledValueType,
                        value = operand,
                        pointer = variableForLocal(instruction.variableId)
                    )
                )
            }

            is ModuleStore -> {
                val moduleVariableUntyped = LlvmOperandLocal(generateName("moduleUntyped"))
                val moduleVariable = LlvmOperandLocal(generateName("module"))
                val fieldPointerVariable = LlvmOperandLocal(generateName("fieldPointer"))
                val fieldValueVariable = LlvmOperandLocal(generateName("fieldValue"))
                val (exportName, exportVariableId) = instruction.exports.single()
                return context.addInstructions(
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
                )
            }

            is PushValue -> {
                val (topLevelEntities, operand) = stackValueToLlvmOperand(instruction.value)
                return context.addTopLevelEntities(topLevelEntities).pushTemporary(operand)
            }

            is Return -> {
                val (context2, returnVariable) = context.popTemporary()

                return context2.addInstructions(
                    LlvmReturn(type = compiledValueType, value = returnVariable)
                )
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

                return context2.addInstructions(
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
                ).pushTemporary(tagValue)
            }

            is TagValueEquals -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.EQ, context = context)
            }

            else -> {
                throw UnsupportedOperationException(instruction.toString())
            }
        }
    }

    internal fun startFunction(): FunctionContext {
        return FunctionContext(
            basicBlockName = generateName("entry"),
            instructions = persistentListOf(),
            stack = persistentListOf(),
            topLevelEntities = persistentListOf(),
            labelPredecessors = persistentMultiMapOf(),
            generateName = ::generateName
        )
    }

    private fun compileBoolNot(context: FunctionContext): FunctionContext {
        val (context2, operand) = context.popTemporary()
        val booleanResult = LlvmOperandLocal(generateName("not_i1"))
        val fullResult = LlvmOperandLocal(generateName("not"))

        val context3 = context2

        return context3.addInstructions(
            LlvmIcmp(
                target = booleanResult,
                conditionCode = LlvmIcmp.ConditionCode.EQ,
                type = compiledBoolType,
                left = operand,
                right = LlvmOperandInt(0)
            ),
            extendBool(target = fullResult, source = booleanResult)
        ).pushTemporary(fullResult)
    }

    private fun compileBoolEquals(context: FunctionContext): FunctionContext {
        return compileBoolComparison(LlvmIcmp.ConditionCode.EQ, context = context)
    }

    private fun compileBoolNotEqual(context: FunctionContext): FunctionContext {
        return compileBoolComparison(LlvmIcmp.ConditionCode.NE, context = context)
    }

    private fun compileBoolComparison(
        conditionCode: LlvmIcmp.ConditionCode,
        context: FunctionContext
    ): FunctionContext {
        return compileComparisonOperation(
            conditionCode = conditionCode,
            context = context,
            operandType = compiledBoolType
        )
    }

    private fun compileCodePointComparison(
        conditionCode: LlvmIcmp.ConditionCode,
        context: FunctionContext
    ): FunctionContext {
        return compileComparisonOperation(
            conditionCode = conditionCode,
            context = context,
            operandType = compiledCodePointType
        )
    }

    private fun compileIntClosedOperation(
        func: (LlvmVariable, LlvmType, LlvmOperand, LlvmOperand) -> LlvmInstruction,
        context: FunctionContext
    ): FunctionContext {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val result = LlvmOperandLocal(generateName("op"))

        return context3
            .addInstructions(func(result, compiledIntType, left, right))
            .pushTemporary(result)
    }

    private fun compileIntComparison(
        conditionCode: LlvmIcmp.ConditionCode,
        context: FunctionContext
    ): FunctionContext {
        return compileComparisonOperation(
            conditionCode = conditionCode,
            context = context,
            operandType = compiledIntType
        )
    }

    private fun compileComparisonOperation(
        conditionCode: LlvmIcmp.ConditionCode,
        context: FunctionContext,
        operandType: LlvmType
    ): FunctionContext {
        val (context2, right) = context.popTemporary()
        val (context3, left) = context2.popTemporary()

        val booleanResult = LlvmOperandLocal(generateName("op_i1"))
        val fullResult = LlvmOperandLocal(generateName("op"))

        return context3
            .addInstructions(
                LlvmIcmp(
                    target = booleanResult,
                    conditionCode = conditionCode,
                    type = operandType,
                    left = left,
                    right = right
                ),
                extendBool(target = fullResult, source = booleanResult)
            )
            .pushTemporary(fullResult)
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
        val rawResult = LlvmOperandLocal(generateName("rawResult"))
        val newString = LlvmOperandLocal(generateName("newString"))
        val newSizePointer = LlvmOperandLocal(generateName("newSizePointer"))
        val newStringData = LlvmOperandLocal(generateName("newStringData"))
        val newStringLeftStart = LlvmOperandLocal(generateName("newStringLeftStart"))
        val newStringLeftStartAsInt = LlvmOperandLocal(generateName("newStringLeftStartAsInt"))
        val newStringRightStartAsInt = LlvmOperandLocal(generateName("newStringRightStartAsInt"))
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
        ).flatten()).pushTemporary(result)
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

    internal fun stackValueToLlvmOperand(
        value: IrValue
    ): Pair<List<LlvmTopLevelEntity>, LlvmOperand> {
        return when (value) {
            is IrBool ->
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(if (value.value) 1 else 0)

            is IrCodePoint ->
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(value.value)

            is IrInt ->
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(value.value.intValueExact())

            is IrString -> {
                val globalName = generateName("string")

                val (stringDefinition, operand) = defineString(
                    globalName = globalName,
                    value = value.value
                )

                listOf(stringDefinition) to operand
            }

            is IrTagValue -> {
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(tagValueToInt(value.value))
            }

            is IrUnit ->
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(0)

            else ->
                throw UnsupportedOperationException(value.toString())
        }
    }

    private var nextTagValueInt = 1
    private val tagValueToInt: MutableMap<TagValue, Int> = mutableMapOf()

    private fun tagValueToInt(tagValue: TagValue): Int {
        if (!tagValueToInt.containsKey(tagValue)) {
            tagValueToInt[tagValue] = nextTagValueInt++
        }

        return tagValueToInt.getValue(tagValue)
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

internal class PersistentMultiMap<K, V>(private val map: PersistentMap<K, PersistentList<V>>) {
    fun add(key: K, value: V): PersistentMultiMap<K, V> {
        return PersistentMultiMap(map.put(key, get(key).add(value)))
    }

    operator fun get(key: K): PersistentList<V> {
        return map.getOrDefault(key, persistentListOf())
    }
}

internal fun <K, V> persistentMultiMapOf(): PersistentMultiMap<K, V> {
    return PersistentMultiMap(persistentMapOf())
}

private fun isTerminator(instruction: LlvmInstruction): Boolean {
    return when (instruction) {
        is LlvmBr -> true
        is LlvmBrUnconditional -> true
        is LlvmReturn -> true
        is LlvmReturnVoid -> true
        else -> false
    }
}
