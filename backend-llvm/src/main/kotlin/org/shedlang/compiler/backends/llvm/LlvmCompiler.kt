package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.NullSource
import org.shedlang.compiler.ast.formatModuleName
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.TagValue
import org.shedlang.compiler.types.Type
import java.nio.file.Path

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

    fun compile(target: Path, mainModule: ModuleName) {
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
                listOf(main)
            ).flatten()
        )

        val source = serialiseProgram(module)

        println(withLineNumbers(source))

        target.toFile().writeText(source)
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
                            targetType = compiledObjectType()
                        )
                    )
                    .addInstructions(
                        fieldAccess(
                            target = field,
                            receiver = instance,
                            fieldName = instruction.fieldName,
                            receiverType = instruction.receiverType!!
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
                    .defineModule(instruction.moduleName) {
                        moduleDefinition(instruction.moduleName)
                    }
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
                        targetType = compiledObjectType()
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
            locals = persistentMapOf(),
            onLocalStore = persistentMultiMapOf(),
            topLevelEntities = persistentListOf(),
            definedModules = persistentSetOf(),
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

    private fun tagValuePointer(target: LlvmOperandLocal, source: LlvmOperandLocal): LlvmGetElementPtr {
        return LlvmGetElementPtr(
            target = target,
            pointerType = compiledObjectType(),
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
        val constructorName = generateName("constructor")
        val constructorPointer = LlvmOperandLocal(generateName("constructorPointer"))
        val instance = LlvmOperandLocal(generateName("instance"))
        val instanceAsValue = LlvmOperandLocal(generateName("instanceAsValue"))
        val tagValue = instruction.tagValue
        val shapeSize = shapeSize(instruction)

        val fieldNames = instruction.fields.map { field -> field.name }
        val parameterNames = fieldNames
            .sorted()
            .map { fieldName -> generateName(fieldName.value) }

        val parameters = parameterNames.map { parameterName -> LlvmParameter(compiledValueType, parameterName) }

        val closureEnvironmentParameter = LlvmParameter(compiledClosureEnvironmentPointerType, generateName("environment"))
        val constructorDefinition = LlvmFunctionDefinition(
            name = constructorName,
            returnType = compiledValueType,
            parameters = listOf(closureEnvironmentParameter) + parameters,
            body = listOf(
                libc.typedMalloc(
                    target = instance,
                    bytes = compiledValueTypeSize * shapeSize,
                    type = compiledObjectType()
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

                fieldNames.zip(parameterNames).flatMap { (fieldName, parameterName) ->
                    val parameter = LlvmOperandLocal(parameterName)
                    val fieldPointer = LlvmOperandLocal(generateName("fieldPointer"))

                    val fieldIndex = fieldIndex(
                        fieldNames = fieldNames,
                        fieldName = fieldName,
                        hasTagValue = tagValue != null
                    )!!
                    listOf(
                        fieldPointer(fieldPointer, instance, fieldIndex),
                        LlvmStore(
                            type = compiledValueType,
                            value = parameter,
                            pointer = fieldPointer
                        )
                    )
                },

                listOf(
                    LlvmPtrToInt(
                        target = instanceAsValue,
                        sourceType = compiledObjectType(),
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
            .let {
                closures.createClosure(
                    target = constructorPointer,
                    functionName = constructorName,
                    parameterTypes = parameters.map { parameter -> parameter.type },
                    freeVariables = listOf(),
                    context = it
                )
            }
            .pushTemporary(constructorPointer)
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
            pointerType = compiledObjectType(),
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

            is IrCodePoint ->
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
    // TODO: handle malloc declaration properly
    return """
        declare i8* @malloc(i64)
        declare i8* @memcpy(i8*, i8*, i64)
        declare i32 @memcmp(i8*, i8*, i64)
        declare i32 @printf(i8* noalias nocapture, ...)
        declare i64 @write(i32, i8*, i64)
    """.trimIndent() + module.serialise()
}

fun withLineNumbers(source: String): String {
    return source.lines().mapIndexed { index, line ->
        (index + 1).toString().padStart(3) + " " + line
    }.joinToString("\n")
}

internal fun fieldIndex(receiverType: Type, fieldName: Identifier): Int {
    val (fields, hasTagValue) = when (receiverType) {
        is ModuleType -> receiverType.fields.keys to false
        is ShapeType -> receiverType.fields.keys to (receiverType.tagValue != null)
        else -> setOf<Identifier>() to false
    }

    val fieldIndex = fields.sorted().indexOf(fieldName)

    if (fieldIndex == -1) {
        throw Exception("could not find field: ${fieldName.value}\nin type: ${receiverType.shortDescription}")
    } else {
        return (if (hasTagValue) 1 else 0) + fieldIndex
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

internal fun shapeSize(instruction: DeclareShape): Int {
    val tagValueOffset = if (instruction.tagValue == null) 0 else 1

    return tagValueOffset + instruction.fields.size
}

internal val compiledUnitValue = LlvmOperandInt(0)
