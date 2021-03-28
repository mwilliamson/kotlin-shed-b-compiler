package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.*
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.Paths

// TODO: Int implementation should be big integers, not i64
internal class LlvmCompiler(
    private val image: Image,
    private val moduleSet: ModuleSet,
    private val irBuilder: LlvmIrBuilder
) {
    private val libc = LibcCallCompiler(irBuilder = irBuilder)
    private val closures = ClosureCompiler(irBuilder = irBuilder, libc = libc)
    private val objects = LlvmObjectCompiler(
        irBuilder = irBuilder,
        libc = libc
    )
    private val modules = ModuleValueCompiler(
        moduleSet = moduleSet,
        objects = objects
    )
    private val strings = StringCompiler(irBuilder = irBuilder, libc = libc)
    private val builtins = BuiltinModuleCompiler(
        moduleSet = moduleSet,
        irBuilder = irBuilder,
        closures = closures,
        libc = libc,
        modules = modules,
        strings = strings
    )
    private val effects = EffectCompiler(
        closures = closures,
        irBuilder = irBuilder,
        libc = libc,
        objects = objects
    )
    private val tuples = LlvmTupleCompiler(
        irBuilder = irBuilder,
        libc = libc
    )

    private val definedModules: MutableSet<ModuleName> = mutableSetOf()

    class CompilationResult(val llvmIr: String, val linkerFiles: List<String>)

    fun compile(mainModule: ModuleName): CompilationResult {
        val defineMainModule = moduleDefinition(mainModule)

        val mainClosureVariable = LlvmOperandLocal("mainClosure")
        val exitCodeVariable = LlvmOperandLocal("exitCode")

        val argcGlobal = LlvmGlobalDefinition(
            name = "shed__argc",
            type = CTypes.argc,
            value = LlvmOperandInt(0)
        )
        val argvGlobal = LlvmGlobalDefinition(
            name = "shed__argv",
            type = CTypes.argv,
            value = LlvmNullPointer
        )

        val main = LlvmFunctionDefinition(
            returnType = compiledValueType,
            name = "main",
            parameters = listOf(
                LlvmParameter(CTypes.argc, "argc"),
                LlvmParameter(CTypes.argv, "argv")
            ),
            body = listOf(
                listOf(
                    LlvmStore(
                        type = CTypes.argc,
                        value = LlvmOperandLocal("argc"),
                        pointer = LlvmOperandGlobal(argcGlobal.name)
                    ),
                    LlvmStore(
                        type = CTypes.argv,
                        value = LlvmOperandLocal("argv"),
                        pointer = LlvmOperandGlobal(argvGlobal.name)
                    ),
                    callModuleInit(mainModule)
                ),
                objects.fieldAccess(
                    modules.modulePointer(mainModule),
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
                listOf(
                    argcGlobal,
                    argvGlobal,
                    main
                ),
                libc.declarations(),
                effects.declarations()
            ).flatten()
        )

        return CompilationResult(llvmIr = serialiseProgram(module), linkerFiles = linkerFiles())
    }

    internal fun linkerFiles(): List<String> {
        val stdlibPath = Paths.get("stdlib-llvm")
        val depsPath = stdlibPath.resolve("deps")

        val files = listOf(
            stdlibPath.resolve("build/libshed.a"),
            depsPath.resolve("gc/.libs/libgc.a"),
            depsPath.resolve("utf8proc/libutf8proc.a")
        )

        return files.map(Path::toString)
    }

    private fun moduleInit(moduleName: ModuleName, context: FunctionContext): FunctionContext {
        return context
            .addTopLevelEntities(defineModule(moduleName))
            .addInstruction(callModuleInit(moduleName))
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
            // TODO: better handling of dependencies
            val context2 = if (moduleName == listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings"))) {
                moduleInit(listOf(Identifier("Core"), Identifier("Options")), context)
            } else {
                context
            }
            return builtins.compileBuiltinModule(moduleName, context = context2)
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

                val callInstructions = closures.callClosure(
                    target = result,
                    closurePointer = receiver,
                    arguments = positionalArguments + namedArguments,
                    tail = instruction.tail,
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

            is DefineFunction -> {
                val freeVariables = findFreeVariables(instruction)

                val paramBindings = mutableListOf<Pair<Int, LlvmOperand>>()

                fun compileParameter(parameter: DefineFunction.Parameter): LlvmParameter {
                    val identifier = generateName(parameter.name)
                    paramBindings.add(parameter.variableId to LlvmOperandLocal(identifier))
                    return LlvmParameter(compiledValueType, identifier)
                }

                val positionalParams = instruction.positionalParameters.map(::compileParameter)
                val namedParams = instruction.namedParameters.map { parameter ->
                    parameter.name to compileParameter(parameter)
                }

                val temporary = LlvmOperandLocal(generateName("value"))

                return closures.compileCreate(
                    target = temporary,
                    functionName = instruction.name,
                    freeVariables = freeVariables,
                    positionalParams = positionalParams,
                    namedParams = namedParams,
                    compileBody = { bodyContext ->
                        compileInstructions(
                            instruction.bodyInstructions,
                            context = bodyContext.localStore(paramBindings),
                        )
                    },
                    context = context,
                ).pushTemporary(temporary)
            }

            is DefineShape -> {
                return compileDeclareShape(instruction, context)
            }

            is Discard -> {
                return context.discardTemporary()
            }

            is Duplicate -> {
                return context.duplicateTemporary()
            }

            is EffectDefine -> {
                val target = generateLocal("effect")

                return effects.define(target = target, effect = instruction.effect, context = context)
                    .pushTemporary(target)
            }

            is EffectHandle -> {
                val (context2, operationHandlers) = context.popTemporaries(instruction.effect.operations.size)
                val (context3, initialState) = if (instruction.hasState) {
                    context2.popTemporary()
                } else {
                    Pair(context2, null)
                }

                val (context4, result) = effects.handle(
                    effect = instruction.effect,
                    compileBody = { context -> compileInstructions(instruction.instructions, context) },
                    operationHandlers = operationHandlers,
                    initialState = initialState,
                    context = context3
                )
                return context4.pushTemporary(result)
            }

            is Exit -> {
                val (context2, returnVariable) = context.popTemporary()

                return context2.addInstructions(effects.exit(returnVariable))
            }

            is FieldAccess -> {
                val (context2, operand) = context.popTemporary()

                val instance = LlvmOperandLocal(generateName("instance"))
                val field = LlvmOperandLocal(generateName("field"))

                return context2
                    .addInstructions(
                        objects.castToObjectPointer(
                            target = instance,
                            value = operand,
                            objectType = instruction.receiverType,
                        )
                    )
                    .addInstructions(
                        objects.fieldAccess(
                            target = field,
                            receiver = instance,
                            fieldName = instruction.fieldName,
                            receiverType = instruction.receiverType
                        )
                    )
                    .pushTemporary(field)
            }

            is FieldUpdate -> {
                val (context2, fieldValue) = context.popTemporary()
                val (context3, receiver) = context2.popTemporary()
                val result = generateLocal("result")

                return context3
                    .addInstructions(objects.updateObject(
                        objectType = instruction.receiverType,
                        existingObjectOperand = receiver,
                        updatedFieldName = instruction.fieldName,
                        updatedFieldValue = fieldValue,
                        target = result,
                    ))
                    .pushTemporary(result)
            }

            is IntAdd -> {
                return compileIntClosedOperation(::LlvmAdd, context = context)
            }

            is IntDivide -> {
                val (context2, right) = context.popTemporary()
                val (context3, left) = context2.popTemporary()

                val isDivisionByZero = generateLocal("is_division_by_zero")
                val result = generateLocal("op")
                val divisionByZeroLabel = createLlvmLabel("division_by_zero")
                val notDivisionByZeroLabel = createLlvmLabel("not_division_by_zero")
                val endLabel = createLlvmLabel("division_end")

                return context3
                    .addInstructions(
                        LlvmIcmp(
                            target = isDivisionByZero,
                            conditionCode = LlvmIcmp.ConditionCode.EQ,
                            type = compiledIntType,
                            left = right,
                            right = LlvmOperandInt(0),
                        ),
                        LlvmBr(
                            condition = isDivisionByZero,
                            ifTrue = divisionByZeroLabel,
                            ifFalse = notDivisionByZeroLabel,
                        ),

                        LlvmLabel(divisionByZeroLabel),
                    )
                    .pushTemporary(shedIntToLlvmOperand(BigInteger.ZERO))
                    .addInstructions(
                        LlvmBrUnconditional(endLabel),
                        LlvmLabel(notDivisionByZeroLabel),
                        LlvmSdiv(
                            target = result,
                            type = compiledIntType,
                            left = left,
                            right = right,
                        ),
                    )
                    .pushTemporary(result)
                    .addInstructions(
                        LlvmBrUnconditional(endLabel),

                        LlvmLabel(endLabel),
                    )
            }

            is IntEquals -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.EQ, context = context)
            }

            is IntLessThan -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.SLT, context = context)
            }

            is IntLessThanOrEqual -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.SLE, context = context)
            }

            is IntGreaterThan -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.SGT, context = context)
            }

            is IntGreaterThanOrEqual -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.SGE, context = context)
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

            is JumpEnd -> {
                return context.addInstructions(
                    LlvmBrUnconditional(labelToLlvmLabel(instruction.destinationLabel))
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
                        ifFalse = labelToLlvmLabel(instruction.destinationLabel)
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
                        ifTrue = labelToLlvmLabel(instruction.destinationLabel),
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
                return moduleInit(instruction.moduleName, context)
            }

            is ModuleInitExit -> {
                return context
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

            is ObjectCreate -> {
                val target = irBuilder.generateLocal("target")
                return context
                    .addInstructions(objects.createObject(
                        objectType = instruction.objectType,
                        fields = listOf(),
                        target = target,
                    ))
                    .pushTemporary(target)
            }

            is PushValue -> {
                val (topLevelEntities, operand) = stackValueToLlvmOperand(instruction.value)
                return context.addTopLevelEntities(topLevelEntities).pushTemporary(operand)
            }

            is Resume -> {
                val (context2, returnVariable) = context.popTemporary()

                return context2.addInstructions(effects.resume(returnVariable))
            }

            is ResumeWithState -> {
                val (context2, newStateVariable) = context.popTemporary()
                val (context3, returnVariable) = context2.popTemporary()

                return context3.addInstructions(effects.resumeWithState(returnVariable, newStateVariable))
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

            is TagValueAccess -> {
                val (context2, operand) = context.popTemporary()
                val tagValue = LlvmOperandLocal(generateName("tagValue"))

                return context2
                    .addInstructions(objects.tagValueAccess(target = tagValue, operand = operand))
                    .pushTemporary(tagValue)
            }

            is TagValueEquals -> {
                return compileIntComparison(LlvmIcmp.ConditionCode.EQ, context = context)
            }

            is TupleAccess -> {
                val (context2, operand) = context.popTemporary()

                val element = LlvmOperandLocal(generateName("element"))

                return context2
                    .addInstructions(tuples.tupleAccess(
                        target = element,
                        receiver = operand,
                        elementIndex = instruction.elementIndex
                    ))
                    .pushTemporary(element)
            }

            is TupleCreate -> {
                val result = LlvmOperandLocal(generateName("result"))

                val (context2, elements) = context.popTemporaries(instruction.length)

                return context2
                    .addInstructions(tuples.tupleCreate(
                        target = result,
                        elements = elements
                    ))
                    .pushTemporary(result)
            }
        }
    }

    internal fun startFunction(): FunctionContext {
        return FunctionContext.initial(generateName = ::generateName)
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

    private fun compileDeclareShape(instruction: DefineShape, context: FunctionContext): FunctionContext {
        val value = LlvmOperandLocal(generateName("value"))
        val shapePointer = LlvmOperandLocal(generateName("shapePointer"))
        val closurePointer = LlvmOperandLocal(irBuilder.generateName("closurePointer"))
        val fieldsObjectPointer = LlvmOperandLocal(irBuilder.generateName("fieldsObjectPointer"))
        val constructorName = generateName("constructor")
        val instanceAsValue = LlvmOperandLocal(generateName("instanceAsValue"))

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
            body = objects.createObject(
                fields = fieldNames.zip(parameterNames.map { parameterName -> LlvmOperandLocal(parameterName) }),
                target = instanceAsValue,
                objectType = instruction.shapeType
            ) + listOf(
                LlvmReturn(
                    type = compiledValueType,
                    value = instanceAsValue
                )
            )
        )

        // TODO: avoid recreating meta-type
        val shapeMetaType = StaticValueType(instruction.shapeType)
        val compiledShapeType = compiledType(shapeMetaType) as CompiledShapeType
        val nameDefinition = strings.defineString(
            irBuilder.generateName(instruction.rawShapeType.name),
            instruction.rawShapeType.name.value,
        )
        val tagValue = compiledType(instruction.shapeType).tagValue

        return context
            .addTopLevelEntities(listOf(constructorDefinition))
            .addInstructions(libc.typedMalloc(shapePointer, compiledShapeType.byteSize(), type = compiledShapeType.llvmPointerType()))
            .addInstructions(LlvmGetElementPtr(
                target = closurePointer,
                pointerType = compiledShapeType.llvmPointerType(),
                pointer = shapePointer,
                indices = listOf(LlvmIndex.zero) + compiledShapeType.closureIndices()
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
            .let {
                if (tagValue == null) {
                    it
                } else {
                    val tagValuePointer = irBuilder.generateLocal("tag_value_ptr")
                    it.addInstructions(
                        LlvmGetElementPtr(
                            target = tagValuePointer,
                            pointerType = compiledShapeType.llvmPointerType(),
                            pointer = shapePointer,
                            indices = listOf(LlvmIndex.zero) + compiledShapeType.typeTagValueIndices()
                        ),
                        LlvmStore(
                            type = compiledTagValueType,
                            value = LlvmOperandInt(objects.tagValueToInt(tagValue)),
                            pointer = tagValuePointer,
                        )
                    )
                }
            }
            .let {
                createFieldsObject(
                    target = fieldsObjectPointer,
                    fieldNames = fieldNames,
                    shapeMetaType = shapeMetaType,
                    shapeType = instruction.shapeType,
                    context = it
                )
            }
            .addTopLevelEntities(nameDefinition)
            .addInstructions(objects.storeObject(
                fields = listOf(
                    Identifier("fields") to fieldsObjectPointer,
                    Identifier("name") to strings.operandRaw(nameDefinition),
                ),
                objectType = shapeMetaType,
                objectPointer = shapePointer
            ))
            .addInstructions(LlvmPtrToInt(
                target = value,
                targetType = compiledValueType,
                value = shapePointer,
                sourceType = compiledShapeType.llvmPointerType()
            ))
            .pushTemporary(value)
    }

    private fun createFieldsObject(
        target: LlvmOperandLocal,
        fieldNames: List<Identifier>,
        shapeMetaType: StaticValueType,
        shapeType: StaticValue,
        context: FunctionContext
    ): FunctionContext {
        val fieldsObjectType = shapeMetaType.fieldType(Identifier("fields"))!!

        val (context3, fieldOperands) = fieldNames.fold(Pair(context, persistentListOf<LlvmOperand>())) { (context2, fieldOperands), fieldName ->

            val defineGetInstruction = defineShapeFieldGet(shapeType = rawValue(shapeType) as Type, fieldName = fieldName)
            val context3 = compileInstruction(defineGetInstruction, context2)
            val (context4, get) = context3.popTemporary()

            val defineUpdateInstruction = defineShapeFieldUpdate(shapeType = rawValue(shapeType) as Type, fieldName = fieldName)
            val context5 = compileInstruction(defineUpdateInstruction, context4)
            val (context6, update) = context5.popTemporary()

            val shapeFieldPointer = LlvmOperandLocal(generateName("shapeFieldPointer"))
            val shapeFieldNameDefinition = strings.defineString(generateName("shapeFieldName"), fieldName.value)

            val context7 = context6
                .addTopLevelEntities(shapeFieldNameDefinition)
                .addInstructions(objects.createObject(
                    target = shapeFieldPointer,
                    objectType = fieldsObjectType.fieldType(fieldName)!!,
                    fields = listOf(
                        Identifier("get") to get,
                        Identifier("name") to strings.operandRaw(shapeFieldNameDefinition),
                        Identifier("update") to update,
                    )
                ))

            Pair(context7, fieldOperands.add(shapeFieldPointer))
        }
        return context3
            .addInstructions(objects.createObject(
                target = target,
                objectType = fieldsObjectType,
                fields = fieldNames.zip(fieldOperands)
            ))
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
    private fun generateLocal(prefix: Identifier) = LlvmOperandLocal(generateName(prefix))
    private fun generateLocal(prefix: String) = LlvmOperandLocal(generateName(prefix))

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
                listOf<LlvmTopLevelEntity>() to shedIntToLlvmOperand(value.value)

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
                listOf<LlvmTopLevelEntity>() to LlvmOperandInt(objects.tagValueToInt(value.value))
            }

            is IrUnit ->
                listOf<LlvmTopLevelEntity>() to compiledUnitValue

            else ->
                throw UnsupportedOperationException(value.toString())
        }
    }

    private fun shedIntToLlvmOperand(value: BigInteger): LlvmOperandInt {
        return LlvmOperandInt(value.toLong())
    }

    private fun moduleType(moduleName: ModuleName) =
        moduleSet.moduleType(moduleName)!!
}

internal fun serialiseProgram(module: LlvmModule): String {
    return module.serialise()
}

internal val compiledUnitValue = LlvmOperandInt(0)
