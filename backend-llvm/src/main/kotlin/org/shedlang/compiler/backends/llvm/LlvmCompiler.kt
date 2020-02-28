package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.NullSource
import org.shedlang.compiler.ast.formatModuleName
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.types.*

internal class Compiler(
    private val image: Image,
    private val moduleSet: ModuleSet,
    private val irBuilder: LlvmIrBuilder
) {
    private val libc = LibcCallCompiler(irBuilder = irBuilder)
    private val closures = ClosureCompiler(irBuilder = irBuilder, libc = libc)
    private val modules = ModuleValueCompiler(irBuilder = irBuilder, moduleSet = moduleSet)
    private val strings = StringCompiler(irBuilder = irBuilder, libc = libc)
    private val builtins = BuiltinModuleCompiler(
        irBuilder = irBuilder,
        closures = closures,
        libc = libc,
        modules = modules,
        strings = strings
    )

    private val definedModules: MutableSet<ModuleName> = mutableSetOf()

    fun compile(mainModule: ModuleName): String {
        val defineMainModule = moduleDefinition(mainModule)

        val mainModuleVariable = LlvmOperandLocal("mainModule")
        val mainClosureVariable = LlvmOperandLocal("mainClosure")
        val exitCodeVariable = LlvmOperandLocal("exitCode")
        val main = LlvmFunctionDefinition(
            returnType = compiledValueType,
            name = "main",
            parameters = listOf(),
            body = listOf(
                importModule(mainModule, target = mainModuleVariable),
                fieldAccess(
                    mainModuleVariable,
                    Identifier("main"),
                    receiverType = moduleType(mainModule),
                    target = mainClosureVariable
                ),
                closures.callClosure(
                    target = exitCodeVariable,
                    closurePointer = mainClosureVariable,
                    arguments = listOf()
                ),
                listOf(
                    LlvmReturn(type = LlvmTypes.i64, value = exitCodeVariable)
                )
            ).flatten()
        )

        val module = LlvmModule(
            listOf(
                defineMainModule,
                listOf(main),
                libc.declarations()
            ).flatten()
        )

        return serialiseProgram(module)
    }

    private fun defineModule(moduleName: ModuleName): List<LlvmTopLevelEntity> {
        if (definedModules.contains(moduleName)) {
            return listOf()
        } else {
            definedModules.add(moduleName)
            return moduleDefinition(moduleName)
        }
    }

    private fun moduleDefinition(moduleName: ModuleName): List<LlvmTopLevelEntity> {
        return listOf(modules.defineModuleValue(moduleName)) + moduleInitDefinition(moduleName)
    }

    private fun moduleInitDefinition(moduleName: ModuleName): List<LlvmTopLevelEntity> {
        val isInitialisedPointer = operandForModuleIsInitialised(moduleName)
        val isInitialised = LlvmOperandLocal(generateName("isInitialised"))

        val bodyContext = compileModuleInitialisation(moduleName, startFunction()).addInstructions(
            LlvmStore(
                type = LlvmTypes.i1,
                value = LlvmOperandInt(1),
                pointer = isInitialisedPointer
            )
        )

        val isInitialisedDefinition = LlvmGlobalDefinition(
            name = nameForModuleIsInitialised(moduleName),
            type = LlvmTypes.i1,
            value = LlvmOperandInt(0)
        )

        val notInitialisedLabel = generateName("notInitialised")
        val initialisedLabel = generateName("initialised")

        val initFunctionDefinition = LlvmFunctionDefinition(
            name = nameForModuleInit(moduleName),
            returnType = LlvmTypes.void,
            parameters = listOf(),
            body = persistentListOf(
                LlvmLoad(
                    target = isInitialised,
                    type = LlvmTypes.i1,
                    pointer = isInitialisedPointer
                ),
                LlvmBr(
                    condition = isInitialised,
                    ifFalse = notInitialisedLabel,
                    ifTrue = initialisedLabel
                ),
                LlvmLabel(notInitialisedLabel)
            ).addAll(bodyContext.instructions).addAll(listOf(
                LlvmReturnVoid,
                LlvmLabel(initialisedLabel),
                LlvmReturnVoid
            ))
        )
        return bodyContext.topLevelEntities.addAll(listOf(
            isInitialisedDefinition,
            initFunctionDefinition
        ))
    }

    private fun compileModuleInitialisation(moduleName: ModuleName, context: FunctionContext): FunctionContext {
        if (builtins.isBuiltinModule(moduleName)) {
            return builtins.compileBuiltinModule(moduleName, context = context)
        } else {
            val moduleInitialisationInstructions = image.moduleInitialisation(moduleName)
            if (moduleInitialisationInstructions == null) {
                throw CompilerError(
                    "could not find initialisation for ${formatModuleName(moduleName)}",
                    source = NullSource
                )
            } else {
                return compileInstructions(
                    moduleInitialisationInstructions,
                    context = context
                )
            }
        }
    }

    internal fun compileInstructions(instructions: List<Instruction>, context: FunctionContext): FunctionContext {
        return instructions.fold(context) { result, instruction ->
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
                val (context2, namedArgumentValues) = context.popTemporaries(instruction.namedArgumentNames.size)
                val (context3, positionalArguments) = context2.popTemporaries(instruction.positionalArgumentCount)
                val (context4, receiver) = context3.popTemporary()
                val result = LlvmOperandLocal(generateName("result"))

                val namedArguments = instruction.namedArgumentNames
                    .zip(namedArgumentValues)
                    .sortedBy { (name, value) -> name }
                    .map { (name, value) -> value }

                val typedArguments = (positionalArguments + namedArguments).map { argument ->
                    LlvmTypedOperand(compiledValueType, argument)
                }

                val callInstructions = closures.callClosure(
                    target = result,
                    closurePointer = receiver,
                    arguments = typedArguments
                )

                return context4.addInstructions(callInstructions).pushTemporary(result)
            }

            is UnicodeScalarEquals -> {
                return compileUnicodeScalarComparison(LlvmIcmp.ConditionCode.EQ, context = context)
            }

            is UnicodeScalarNotEqual -> {
                return compileUnicodeScalarComparison(LlvmIcmp.ConditionCode.NE, context = context)
            }

            is UnicodeScalarGreaterThanOrEqual -> {
                return compileUnicodeScalarComparison(LlvmIcmp.ConditionCode.UGE, context = context)
            }

            is UnicodeScalarGreaterThan -> {
                return compileUnicodeScalarComparison(LlvmIcmp.ConditionCode.UGT, context = context)
            }

            is UnicodeScalarLessThanOrEqual -> {
                return compileUnicodeScalarComparison(LlvmIcmp.ConditionCode.ULE, context = context)
            }

            is UnicodeScalarLessThan -> {
                return compileUnicodeScalarComparison(LlvmIcmp.ConditionCode.ULT, context = context)
            }

            is DeclareFunction -> {
                val functionName = generateName(instruction.name)
                val temporary = LlvmOperandLocal(generateName("value"))

                val irParameters = instruction.positionalParameters + instruction.namedParameters
                    .sortedBy { namedParameter -> namedParameter.name }

                val llvmParameters = irParameters.map { irParameter ->
                    LlvmParameter(compiledValueType, generateName(irParameter.name))
                }
                val llvmParameterTypes = llvmParameters.map(LlvmParameter::type)


                val freeVariables = closures.findFreeVariables(instruction)

                val closureEnvironmentParameter = LlvmParameter(compiledClosureEnvironmentPointerType, generateName("environment"))
                val bodyContextWithEnvironment = closures.loadFreeVariables(
                    freeVariables = freeVariables,
                    closureEnvironmentPointer = LlvmOperandLocal(closureEnvironmentParameter.name),
                    context = startFunction()
                )
                val bodyContextWithEnvironmentAndParameters =
                    irParameters.zip(llvmParameters).fold(bodyContextWithEnvironment) { bodyContext, (irParameter, llvmParameter) ->
                        bodyContext.localStore(irParameter.variableId, LlvmOperandLocal(llvmParameter.name))
                    }

                val bodyContext = compileInstructions(
                    instruction.bodyInstructions,
                    context = bodyContextWithEnvironmentAndParameters
                )
                val functionDefinition = LlvmFunctionDefinition(
                    name = functionName,
                    returnType = compiledValueType,
                    parameters = listOf(closureEnvironmentParameter) + llvmParameters,
                    body = bodyContext.instructions
                )

                return context
                    .addTopLevelEntities(bodyContext.topLevelEntities)
                    .addTopLevelEntities(listOf(functionDefinition))
                    .let {
                        closures.createClosure(
                            target = temporary,
                            functionName = functionName,
                            parameterTypes = llvmParameterTypes,
                            freeVariables = freeVariables,
                            context = it
                        )
                    }
                    .pushTemporary(temporary)
            }

            is DeclareShape -> {
                return compileDeclareShape(instruction, context)
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

            is FieldAccess -> {
                val (context2, operand) = context.popTemporary()

                val instance = LlvmOperandLocal(generateName("instance"))
                val field = LlvmOperandLocal(generateName("field"))

                return context2
                    .addInstructions(
                        LlvmIntToPtr(
                            target = instance,
                            sourceType = compiledValueType,
                            value = operand,
                            targetType = compiledObjectPointerType()
                        )
                    )
                    .addInstructions(
                        fieldAccess(
                            target = field,
                            receiver = instance,
                            fieldName = instruction.fieldName,
                            receiverType = instruction.receiverType
                        )
                    )
                    .pushTemporary(field)
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
                return context.pushTemporary(context.localLoad(instruction.variableId))
            }

            is LocalStore -> {
                val (context2, operand) = context.popTemporary()
                return context2.localStore(instruction.variableId, operand)
            }

            is ModuleInit -> {
                return context
                    .addTopLevelEntities(defineModule(instruction.moduleName))
                    .addInstruction(callModuleInit(instruction.moduleName))
            }

            is ModuleLoad -> {
                val moduleValue = LlvmOperandLocal(generateName("moduleValue"))
                val loadModule = modules.loadRaw(
                    target = moduleValue,
                    moduleName = instruction.moduleName
                )
                return context
                    .addInstruction(loadModule)
                    .pushTemporary(moduleValue)
            }

            is ModuleStore -> {
                return context.addInstructions(modules.storeFields(
                    moduleName = instruction.moduleName,
                    exports = instruction.exports.map { (exportName, exportVariableId) ->
                        exportName to context.localLoad(exportVariableId)
                    }
                ))
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
                return strings.compileStringAdd(instruction, context = context)
            }

            is StringEquals -> {
                return strings.compileStringEquals(context = context)
            }

            is StringNotEqual -> {
                return strings.compileStringNotEqual(context = context)
            }

            is Swap -> {
                val (context2, operand1) = context.popTemporary()
                val (context3, operand2) = context2.popTemporary()
                return context3.pushTemporary(operand1).pushTemporary(operand2)
            }

            is SymbolEquals -> {
                throw UnsupportedOperationException(instruction.toString())
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
                        targetType = compiledObjectPointerType()
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

            is TupleAccess -> {
                val (context2, operand) = context.popTemporary()

                val tuple = LlvmOperandLocal(generateName("tuple"))
                val elementPointer = LlvmOperandLocal(generateName("elementPointer"))
                val element = LlvmOperandLocal(generateName("element"))

                return context2.addInstructions(
                    LlvmIntToPtr(
                        target = tuple,
                        sourceType = compiledValueType,
                        value = operand,
                        targetType = compiledTupleType
                    ),
                    tupleElementPointer(elementPointer, tuple, instruction.elementIndex),
                    LlvmLoad(
                        target = element,
                        type = compiledValueType,
                        pointer = elementPointer
                    )
                ).pushTemporary(element)
            }

            is TupleCreate -> {
                val tuple = LlvmOperandLocal(generateName("tuple"))
                val result = LlvmOperandLocal(generateName("result"))

                val context2 = context.addInstructions(
                    libc.typedMalloc(
                        target = tuple,
                        bytes = compiledValueTypeSize * instruction.length,
                        type = compiledTupleType
                    )
                )

                val (context3, elements) = context2.popTemporaries(instruction.length)

                val context4 = elements.foldIndexed(context3) { elementIndex, newContext, element ->
                    val elementPointer = LlvmOperandLocal(generateName("element"))
                    newContext.addInstructions(
                        tupleElementPointer(elementPointer, tuple, elementIndex),
                        LlvmStore(
                            type = compiledValueType,
                            value = element,
                            pointer = elementPointer
                        )
                    )
                }

                return context4.addInstructions(
                    LlvmPtrToInt(
                        target = result,
                        sourceType = compiledTupleType,
                        value = tuple,
                        targetType = compiledValueType
                    )
                ).pushTemporary(result)
            }
        }
    }

    internal fun startFunction(): FunctionContext {
        return FunctionContext(
            basicBlockName = generateName("entry"),
            instructions = persistentListOf(),
            stack = persistentListOf(),
            locals = persistentMapOf(),
            onLocalStore = persistentMultiMapOf(),
            topLevelEntities = persistentListOf(),
            labelPredecessors = persistentMultiMapOf(),
            generateName = ::generateName
        )
    }

    private fun compileBoolNot(context: FunctionContext): FunctionContext {
        val (context2, operand) = context.popTemporary()
        val booleanResult = LlvmOperandLocal(generateName("not_i1"))
        val fullResult = LlvmOperandLocal(generateName("not"))

        return context2.addInstructions(
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

    private fun compileUnicodeScalarComparison(
        conditionCode: LlvmIcmp.ConditionCode,
        context: FunctionContext
    ): FunctionContext {
        return compileComparisonOperation(
            conditionCode = conditionCode,
            context = context,
            operandType = compiledUnicodeScalarType
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

    private fun tagValuePointer(target: LlvmOperandLocal, source: LlvmOperand): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledObjectPointerType(),
            pointer = source,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
            )
        )
    }

    private fun tupleElementPointer(target: LlvmOperandLocal, receiver: LlvmOperandLocal, elementIndex: Int): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledTupleType,
            pointer = receiver,
            indices = listOf(
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i64, LlvmOperandInt(elementIndex))
            )
        )
    }

    private fun compileDeclareShape(instruction: DeclareShape, context: FunctionContext): FunctionContext {
        val value = LlvmOperandLocal(generateName("value"))
        val shapePointer = LlvmOperandLocal(generateName("shapePointer"))
        val closurePointer = LlvmOperandLocal(irBuilder.generateName("closurePointer"))
        val objectPointer = LlvmOperandLocal(irBuilder.generateName("objectPointer"))
        val objectPointerZeroSize = LlvmOperandLocal(irBuilder.generateName("objectPointerZeroSize"))
        val fieldsObjectPointer = LlvmOperandLocal(irBuilder.generateName("fieldsObjectPointer"))
        val constructorName = generateName("constructor")
        val constructorPointer = LlvmOperandLocal(generateName("constructorPointer"))
        val instanceAsValue = LlvmOperandLocal(generateName("instanceAsValue"))
        val tagValue = instruction.tagValue

        val fieldNames = instruction.fields.map { field -> field.name }
        val parameterNames = fieldNames
            .sorted()
            .map { fieldName -> generateName(fieldName.value) }

        val parameters = parameterNames.map { parameterName -> LlvmParameter(compiledValueType, parameterName) }
        val parameterTypes = parameters.map { parameter -> parameter.type }

        val closureEnvironmentParameter = LlvmParameter(compiledClosureEnvironmentPointerType, generateName("environment"))
        val constructorDefinition = LlvmFunctionDefinition(
            name = constructorName,
            returnType = compiledValueType,
            parameters = listOf(closureEnvironmentParameter) + parameters,
            body = createObject(
                tagValue = tagValue,
                fields = fieldNames.zip(parameterNames.map { parameterName -> LlvmOperandLocal(parameterName) }),
                target = instanceAsValue
            ) + listOf(
                LlvmReturn(
                    type = compiledValueType,
                    value = instanceAsValue
                )
            )
        )

        val shapePointerType = compiledShapePointerType(
            fieldCount = instruction.fields.size,
            parameterTypes = parameterTypes
        )
        val closurePointerType = compiledClosurePointerType(parameterTypes = parameterTypes)

        return context
            .addTopLevelEntities(listOf(constructorDefinition))
            .addInstructions(libc.typedMalloc(shapePointer, compiledShapeSize(), type = shapePointerType))
            .addInstructions(LlvmGetElementPtr(
                target = closurePointer,
                pointerType = shapePointerType,
                pointer = shapePointer,
                indices = listOf(
                    LlvmIndex.i64(0),
                    LlvmIndex.i32(0)
                )
            ))
            .let {
                closures.storeClosure(
                    closurePointer = closurePointer,
                    functionName = constructorName,
                    parameterTypes = parameterTypes,
                    freeVariables = listOf(),
                    context = it
                )
            }
            .addInstructions(LlvmGetElementPtr(
                target = objectPointer,
                pointerType = shapePointerType,
                pointer = shapePointer,
                indices = listOf(
                    LlvmIndex.i64(0),
                    LlvmIndex.i32(1)
                )
            ))
            .let {
                createFieldsObject(
                    target = fieldsObjectPointer,
                    fieldNames = fieldNames,
                    context = it
                )
            }
            .addInstructions(LlvmBitCast(
                target = objectPointerZeroSize,
                sourceType = compiledObjectPointerType(size = instruction.fields.size),
                value = objectPointer,
                targetType = compiledObjectPointerType(size = 0)
            ))
            .addInstructions(storeObject(
                fields = listOf(
                    Identifier("fields") to fieldsObjectPointer
                ),
                tagValue = null,
                objectPointer = objectPointerZeroSize
            ))
            .addInstructions(LlvmPtrToInt(
                target = value,
                targetType = compiledValueType,
                value = shapePointer,
                sourceType = shapePointerType
            ))
            .pushTemporary(value)
    }

    private fun createFieldsObject(
        target: LlvmOperandLocal,
        fieldNames: List<Identifier>,
        context: FunctionContext
    ): FunctionContext {
        val (context3, fieldOperands) = fieldNames.fold(Pair(context, persistentListOf<LlvmOperand>())) { (context2, fieldOperands), fieldName ->
            val shapeFieldPointer = LlvmOperandLocal(generateName("shapeFieldPointer"))
            val shapeFieldNameDefinition = strings.defineString(generateName("shapeFieldName"), fieldName.value)
            val get = LlvmOperandLocal(generateName("get"))
            val getResult = LlvmOperandLocal(generateName("getResult"))
            val objectPointer = LlvmOperandLocal("obj")
            val getFunctionDefinition = LlvmFunctionDefinition(
                name = generateName("get"),
                returnType = compiledValueType,
                parameters = listOf(
                    LlvmParameter(compiledClosureEnvironmentPointerType, "environment"),
                    LlvmParameter(type = compiledValueType, name = "param")
                ),
                body = persistentListOf<LlvmInstruction>()
                    .add(LlvmIntToPtr(
                        target = objectPointer,
                        sourceType = compiledValueType,
                        value = LlvmOperandLocal("param"),
                        targetType = compiledObjectPointerType()
                    ))
                    .addAll(fieldAccess(
                        target = getResult,
                        receiver = objectPointer,
                        fieldName = fieldName,
                        fieldNames = fieldNames,
                        tagValue = null
                    ))
                    .add(LlvmReturn(type = compiledValueType, value = getResult))
            )

            val context3 = context2
                .addTopLevelEntities(shapeFieldNameDefinition)
                .addTopLevelEntities(getFunctionDefinition)
                .let {
                    closures.createClosure(
                        target = get,
                        functionName = getFunctionDefinition.name,
                        parameterTypes = listOf(compiledValueType),
                        freeVariables = listOf(),
                        context = it
                    )
                }
                .addInstructions(createObject(
                    target = shapeFieldPointer,
                    tagValue = null,
                    fields = listOf(
                        Identifier("get") to get,
                        Identifier("name") to strings.operandRaw(shapeFieldNameDefinition)
                    )
                ))

            Pair(context3, fieldOperands.add(shapeFieldPointer))
        }
        return context3
            .addInstructions(createObject(
                target = target,
                tagValue = null,
                fields = fieldNames.zip(fieldOperands)
            ))
    }

    private fun createObject(tagValue: TagValue?, fields: List<Pair<Identifier, LlvmOperand>>, target: LlvmOperandLocal): List<LlvmInstruction> {
        val instance = LlvmOperandLocal(generateName("instance"))
        val fieldNames = fields.map { (fieldName, fieldValue) -> fieldName }
        val shapeSize = shapeSize(
            hasTagValue = tagValue != null,
            fieldNames = fieldNames
        )
        return listOf(
            libc.typedMalloc(
                target = instance,
                bytes = compiledValueTypeSize * shapeSize,
                type = compiledObjectPointerType()
            ),

            storeObject(
                fields = fields,
                tagValue = tagValue,
                objectPointer = instance
            ),

            listOf(
                LlvmPtrToInt(
                    target = target,
                    sourceType = compiledObjectPointerType(),
                    value = instance,
                    targetType = compiledValueType
                )
            )
        ).flatten()
    }

    private fun storeObject(
        fields: List<Pair<Identifier, LlvmOperand>>,
        tagValue: TagValue?,
        objectPointer: LlvmOperand
    ): List<LlvmInstruction> {
        val fieldNames = fields.map { (fieldName, _) -> fieldName }

        val storeTagValue = if (tagValue == null) {
            listOf()
        } else {
            val tagValuePointer = LlvmOperandLocal(generateName("tagValuePointer"))

            listOf(
                tagValuePointer(tagValuePointer, objectPointer),
                LlvmStore(
                    type = compiledTagValueType,
                    value = LlvmOperandInt(tagValueToInt(tagValue)),
                    pointer = tagValuePointer
                )
            )
        }

        val storeFields = fields.flatMap { (fieldName, fieldValue) ->
            val fieldPointer = LlvmOperandLocal(generateName("fieldPointer"))

            val fieldIndex = fieldIndex(
                fieldNames = fieldNames,
                fieldName = fieldName,
                hasTagValue = tagValue != null
            )!!
            listOf(
                fieldPointer(fieldPointer, objectPointer, fieldIndex),
                LlvmStore(
                    type = compiledValueType,
                    value = fieldValue,
                    pointer = fieldPointer
                )
            )
        }

        return storeTagValue + storeFields
    }

    // TODO: remove duplication of fieldAccess()
    private fun fieldAccess(receiver: LlvmOperand, fieldName: Identifier, fieldNames: List<Identifier>, tagValue: TagValue?, target: LlvmVariable): List<LlvmInstruction> {
        val fieldPointerVariable = LlvmOperandLocal(generateName("fieldPointer"))

        val fieldIndex = fieldIndex(
            fieldNames = fieldNames,
            hasTagValue = tagValue != null,
            fieldName = fieldName
        )!!
        return listOf(
            fieldPointer(fieldPointerVariable, receiver, fieldIndex),
            LlvmLoad(
                target = target,
                type = compiledValueType,
                pointer = fieldPointerVariable
            )
        )
    }

    private fun fieldAccess(receiver: LlvmOperand, fieldName: Identifier, receiverType: Type, target: LlvmVariable): List<LlvmInstruction> {
        val fieldPointerVariable = LlvmOperandLocal(generateName("fieldPointer"))

        return listOf(
            fieldPointer(fieldPointerVariable, receiver, fieldIndex(receiverType, fieldName)),
            LlvmLoad(
                target = target,
                type = compiledValueType,
                pointer = fieldPointerVariable
            )
        )
    }

    private fun fieldPointer(target: LlvmOperandLocal, receiver: LlvmOperand, fieldIndex: Int): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledObjectPointerType(),
            pointer = receiver,
            indices = listOf(
                LlvmIndex(LlvmTypes.i32, LlvmOperandInt(0)),
                LlvmIndex(LlvmTypes.i32, LlvmOperandInt(fieldIndex))
            )
        )
    }

    private fun importModule(moduleName: ModuleName, target: LlvmVariable): List<LlvmInstruction> {
        return listOf(
            callModuleInit(moduleName),
            modules.load(target = target, moduleName = moduleName)
        )
    }

    private fun callModuleInit(moduleName: ModuleName): LlvmCall {
        return LlvmCall(
            target = null,
            returnType = LlvmTypes.void,
            functionPointer = operandForModuleInit(moduleName),
            arguments = listOf()
        )
    }

    private fun operandForModuleInit(moduleName: ModuleName): LlvmOperand {
        return LlvmOperandGlobal(nameForModuleInit(moduleName))
    }

    private fun operandForModuleIsInitialised(moduleName: ModuleName): LlvmOperand {
        return LlvmOperandGlobal(nameForModuleIsInitialised(moduleName))
    }

    private fun nameForModuleInit(moduleName: ModuleName): String {
        return "shed__module_init__${serialiseModuleName(moduleName)}"
    }

    private fun nameForModuleIsInitialised(moduleName: ModuleName): String {
        return "shed__module_is_initialised__${serialiseModuleName(moduleName)}"
    }

    private fun serialiseModuleName(moduleName: ModuleName) =
        moduleName.joinToString("_") { part -> part.value }

    private fun generateName(prefix: Identifier) = irBuilder.generateName(prefix)
    private fun generateName(prefix: String) = irBuilder.generateName(prefix)

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

            is IrUnicodeScalar ->
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(value.value)

            is IrInt ->
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(value.value.intValueExact())

            is IrString -> {
                val globalName = generateName("string")

                val stringDefinition = strings.defineString(
                    globalName = globalName,
                    value = value.value
                )

                val operand = strings.operandRaw(stringDefinition)

                listOf(stringDefinition) to operand
            }

            is IrTagValue -> {
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(tagValueToInt(value.value))
            }

            is IrUnit ->
                listOf<LlvmTopLevelEntity>() to compiledUnitValue

            else ->
                throw UnsupportedOperationException(value.toString())
        }
    }

    private fun moduleType(moduleName: ModuleName) =
        moduleSet.moduleType(moduleName)!!

    private var nextTagValueInt = 1
    private val tagValueToInt: MutableMap<TagValue, Int> = mutableMapOf()

    private fun tagValueToInt(tagValue: TagValue): Int {
        if (!tagValueToInt.containsKey(tagValue)) {
            tagValueToInt[tagValue] = nextTagValueInt++
        }

        return tagValueToInt.getValue(tagValue)
    }
}

internal fun serialiseProgram(module: LlvmModule): String {
    return module.serialise()
}

fun withLineNumbers(source: String): String {
    return source.lines().mapIndexed { index, line ->
        (index + 1).toString().padStart(3) + " " + line
    }.joinToString("\n")
}

internal fun fieldIndex(receiverType: Type, fieldName: Identifier): Int {
    if (receiverType is MetaType && fieldName == Identifier("fields")) {
        // TODO: use a more principled derivation (split out calculation of offset of the entire field set?)
        // It's one since a shape is made of two parts:
        //   * a closure with zero free variables
        //   * the fields of the shape (as in the type, rather than an instance)
        // A closure with zero free variables is a pointer, which is the same size as a field
        // A shape only has one field i.e. "fields"
        // Therefore, that field is always at a field index of 1
        return 1
    } else {
        val (fieldNames, hasTagValue) = when (receiverType) {
            is ModuleType -> receiverType.fields.keys to false
            is ShapeType -> receiverType.fields.keys to (receiverType.tagValue != null)
            else -> setOf<Identifier>() to false
        }

        val fieldIndex = fieldNames.sorted().indexOf(fieldName)

        if (fieldIndex == -1) {
            throw Exception("could not find field: ${fieldName.value}\nin type: ${receiverType.shortDescription}")
        } else {
            return (if (hasTagValue) 1 else 0) + fieldIndex
        }
    }
}

internal fun fieldIndex(
    fieldNames: Collection<Identifier>,
    fieldName: Identifier,
    hasTagValue: Boolean
): Int? {
    val fieldIndex = fieldNames.sorted().indexOf(fieldName)

    return if (fieldIndex == -1) {
        null
    } else {
        (if (hasTagValue) 1 else 0) + fieldIndex
    }
}

internal fun shapeSize(fieldNames: Collection<Identifier>, hasTagValue: Boolean): Int {
    val tagValueOffset = if (hasTagValue) 1 else 0

    return tagValueOffset + fieldNames.size
}

internal val compiledUnitValue = LlvmOperandInt(0)
