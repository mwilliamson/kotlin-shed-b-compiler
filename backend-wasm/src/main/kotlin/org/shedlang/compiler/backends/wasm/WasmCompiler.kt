package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.*
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.NullSource
import org.shedlang.compiler.ast.formatModuleName
import org.shedlang.compiler.backends.wasm.runtime.callMalloc
import org.shedlang.compiler.backends.wasm.runtime.compileRuntime
import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasi
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.Field
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.TagValue
import java.lang.UnsupportedOperationException

// TODO: Int implementation should be big integers, not i32
internal class WasmCompilationResult(
    val tagValuesToInt: Map<TagValue, Int>,
    val module: WasmModule,
)

internal class WasmCompiler(private val image: Image, private val moduleSet: ModuleSet) {
    fun compile(mainModule: ModuleName): WasmCompilationResult {
        val startFunctionContext = compileStartFunction(mainModule)

        var globalContext = startFunctionContext.merge(compileRuntime())

        globalContext = compileDependencies(globalContext)

        return globalContext.toModule()
    }

    fun compileDependencies(initialContext: WasmGlobalContext): WasmGlobalContext {
        var context = initialContext
        while (true) {
            val missingDependencies = context.missingDependencies()
            if (missingDependencies.isEmpty()) {
                return context
            }

            val dependencyContexts = missingDependencies.map { dependency -> compileModule(dependency) }

            context = context.merge(WasmGlobalContext.merge(dependencyContexts))
        }
    }

    private fun compileStartFunction(mainModule: ModuleName): WasmGlobalContext {
        val moduleType = moduleSet.moduleType(mainModule)!!
        return WasmFunctionContext.initial()
            .let { compileModuleInitCall(mainModule, context = it) }
            .addInstruction(WasmModules.compileLoad(mainModule))
            .addInstruction(WasmObjects.compileFieldLoad(objectType = moduleType, fieldName = Identifier("main")))
            .let { compileCall(positionalArgumentCount = 0, namedArgumentNames = listOf(), context = it) }
            .addInstruction(Wasi.callProcExit())
            .toStaticFunctionInGlobalContext(
                identifier = WasmNaming.Wasi.start,
                export = true,
            )
    }

    private fun compileModule(moduleName: ModuleName): WasmGlobalContext {
        val moduleInit = image.moduleInitialisation(moduleName)
        val nativeModuleInit = WasmNativeModules.moduleInitialisation(moduleName)
        val initFunctionIdentifier = WasmNaming.moduleInit(moduleName)

        val isInited = WasmNaming.moduleIsInited(moduleName)
        val initContext = WasmFunctionContext.initial()
            .addMutableGlobal(
                identifier = isInited,
                type = Wasm.T.i32,
                initial = Wasm.I.i32Const(0),
            )
            .addInstruction(Wasm.I.globalGet(isInited))
            .addInstruction(Wasm.I.if_())
            .addInstruction(Wasm.I.else_)

        val finalInitContext = if (moduleInit != null) {
            compileInstructions(moduleInit, initContext)
        } else if (nativeModuleInit != null) {
            val (initContext2, exports) = nativeModuleInit(initContext)
            moduleStore(
                moduleName = moduleName,
                exports = exports,
                context = initContext2,
            )
        } else {
            throw CompilerError(message = "module not found: ${formatModuleName(moduleName)}", source = NullSource)
        }
        return finalInitContext
            .addInstructions(Wasm.I.end)
            .toStaticFunctionInGlobalContext(identifier = initFunctionIdentifier)
            .addModuleName(moduleName)
    }

    internal fun compileInstructions(instructions: List<Instruction>, context: WasmFunctionContext): WasmFunctionContext {
        return instructions.foldIndexed(context, { instructionIndex, currentContext, instruction ->
            compileInstruction(instruction, currentContext)
        })
    }

    private fun compileInstruction(
        instruction: Instruction,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        when (instruction) {
            is BoolEquals -> {
                return context.addInstruction(Wasm.I.i32Equals)
            }

            is BoolNotEqual -> {
                return context.addInstruction(Wasm.I.i32NotEqual)
            }

            is BoolNot -> {
                return addBoolNot(context)
            }

            is Call -> {
                return compileCall(
                    positionalArgumentCount = instruction.positionalArgumentCount,
                    namedArgumentNames = instruction.namedArgumentNames,
                    context = context
                )
            }

            is DefineFunction -> {
                return compileDefineFunction(instruction, context)
            }

            is DefineShape -> {
                return compileDefineShape(instruction, context)
            }

            is Discard -> {
                return context.addInstruction(Wasm.I.drop)
            }

            is Duplicate -> {
                val (context2, temp) = context.addLocal("duplicate")
                return context2
                    .addInstruction(Wasm.I.localSet(temp))
                    .addInstruction(Wasm.I.localGet(temp))
                    .addInstruction(Wasm.I.localGet(temp))
            }

            is FieldAccess -> {
                return context.addInstruction(WasmObjects.compileFieldLoad(
                    objectType = instruction.receiverType,
                    fieldName = instruction.fieldName
                ))
            }

            is IntAdd -> {
                return context.addInstruction(Wasm.I.i32Add)
            }

            is IntDivide -> {
                val (context2, left) = context.addLocal("left")
                val (context3, right) = context2.addLocal("left")
                return context3
                    .addInstruction(Wasm.I.localSet(right))
                    .addInstruction(Wasm.I.localSet(left))
                    .addInstruction(Wasm.I.if_(
                        results = listOf(WasmData.intType),
                        condition = Wasm.I.i32Equals(Wasm.I.localGet(right), Wasm.I.i32Const(0)),
                        ifTrue = listOf(Wasm.I.i32Const(0)),
                        ifFalse = listOf(Wasm.I.i32DivideSigned(Wasm.I.localGet(left), Wasm.I.localGet(right))),
                    ))
            }

            is IntEquals -> {
                return context.addInstruction(Wasm.I.i32Equals)
            }

            is IntGreaterThan -> {
                return context.addInstruction(Wasm.I.i32GreaterThanSigned)
            }

            is IntGreaterThanOrEqual -> {
                return context.addInstruction(Wasm.I.i32GreaterThanOrEqualSigned)
            }

            is IntLessThan -> {
                return context.addInstruction(Wasm.I.i32LessThanSigned)
            }

            is IntLessThanOrEqual -> {
                return context.addInstruction(Wasm.I.i32LessThanOrEqualSigned)
            }

            is IntMinus -> {
                val (context2, local) = context.addLocal()
                return context2
                    .addInstruction(Wasm.I.localSet(local))
                    .addInstruction(Wasm.I.i32Sub(Wasm.I.i32Const(0), Wasm.I.localGet(local)))
            }

            is IntMultiply -> {
                return context.addInstruction(Wasm.I.i32Multiply)
            }

            is IntNotEqual -> {
                return context.addInstruction(Wasm.I.i32NotEqual)
            }

            is IntSubtract -> {
                return context.addInstruction(Wasm.I.i32Sub)
            }

            is JumpEnd -> {
                return context
            }

            is JumpIfFalse -> {
                return addJumpIfFalse(
                    label = instruction.destinationLabel,
                    joinLabel = instruction.endLabel,
                    context = context,
                )
            }

            is JumpIfTrue -> {
                return addJumpIfFalse(
                    label = instruction.destinationLabel,
                    joinLabel = instruction.endLabel,
                    context = addBoolNot(context),
                )
            }

            is Label -> {
                return context.onLabel(instruction.value).fold(context, WasmFunctionContext::addInstruction)
            }

            is LocalLoad -> {
                val identifier = context.variableToStoredLocal(
                    variableId = instruction.variableId,
                )
                return context.addInstruction(Wasm.I.localGet(identifier))
            }

            is LocalStore -> {
                val (context2, identifier) = context.variableToLocal(
                    variableId = instruction.variableId,
                    name = instruction.name,
                )
                val context3 = context2.addInstruction(Wasm.I.localSet(identifier))
                return context3.onLocalStore(instruction.variableId)
            }

            is ModuleInit -> {
                return compileModuleInitCall(instruction.moduleName, context)
            }

            is ModuleInitExit -> {
                return context
            }

            is ModuleLoad -> {
                return context.addInstruction(WasmModules.compileLoad(instruction.moduleName))
            }

            is ModuleStore -> {
                val (context2, exports) = instruction.exports.fold(Pair(
                    context,
                    persistentListOf<Pair<Identifier, WasmInstruction.Folded>>(),
                )) { (currentContext, currentExports), (exportName, exportVariableId) ->
                    val (currentContext2, exportValue) = currentContext.variableToLocal(exportVariableId, exportName)
                    Pair(
                        currentContext2,
                        currentExports.add(Pair(exportName, Wasm.I.localGet(exportValue)))
                    )
                }

                return moduleStore(
                    moduleName = instruction.moduleName,
                    exports = exports,
                    context = context2,
                )
            }

            is PushValue -> {
                val value = instruction.value
                when (value) {
                    is IrBool -> {
                        val intValue = if (value.value) 1 else 0
                        return context.addInstruction(Wasm.I.i32Const(intValue))
                    }
                    is IrInt -> {
                        return context.addInstruction(Wasm.I.i32Const(value.value.intValueExact()))
                    }
                    is IrString -> {
                        val (context2, memoryIndex) = context.addSizedStaticUtf8String(value.value)

                        return context2.addInstruction(Wasm.I.i32Const(memoryIndex))
                    }
                    is IrTagValue -> {
                        val context2 = context.compileTagValue(value.value)
                        return context2.addInstruction(Wasm.I.i32Const(value.value))
                    }
                    is IrUnicodeScalar -> {
                        return context.addInstruction(Wasm.I.i32Const(value.value))
                    }
                    is IrUnit -> {
                        return context.addInstruction(WasmData.unitValue)
                    }
                    else -> {
                        throw UnsupportedOperationException("unhandled IR value: $value")
                    }
                }
            }

            is Return -> {
                return context
            }

            is StringAdd -> {
                return context.addInstruction(Wasm.I.call(WasmNaming.Runtime.stringAdd))
            }

            is StringEquals -> {
                return context.addInstruction(Wasm.I.call(WasmNaming.Runtime.stringEquals))
            }

            is StringNotEqual -> {
                return addBoolNot(context.addInstruction(Wasm.I.call(WasmNaming.Runtime.stringEquals)))
            }

            is Swap -> {
                val (context2, temp1) = context.addLocal()
                val (context3, temp2) = context2.addLocal()
                return context3
                    .addInstruction(Wasm.I.localSet(temp1))
                    .addInstruction(Wasm.I.localSet(temp2))
                    .addInstruction(Wasm.I.localGet(temp1))
                    .addInstruction(Wasm.I.localGet(temp2))
            }

            is TagValueAccess -> {
                return WasmObjects.compileTagValueAccess(context)
            }

            is TagValueEquals -> {
                return context.addInstruction(Wasm.I.i32Equals)
            }

            is TupleAccess -> {
                return WasmTuples.compileAccess(
                    elementIndex = instruction.elementIndex,
                    context = context,
                )
            }

            is TupleCreate -> {
                return WasmTuples.compileCreate(
                    length = instruction.length,
                    context = context,
                )
            }

            is UnicodeScalarEquals -> {
                return context.addInstruction(Wasm.I.i32Equals)
            }

            is UnicodeScalarGreaterThan -> {
                return context.addInstruction(Wasm.I.i32GreaterThanUnsigned)
            }

            is UnicodeScalarGreaterThanOrEqual -> {
                return context.addInstruction(Wasm.I.i32GreaterThanOrEqualUnsigned)
            }

            is UnicodeScalarLessThan -> {
                return context.addInstruction(Wasm.I.i32LessThanUnsigned)
            }

            is UnicodeScalarLessThanOrEqual -> {
                return context.addInstruction(Wasm.I.i32LessThanOrEqualUnsigned)
            }

            is UnicodeScalarNotEqual -> {
                return context.addInstruction(Wasm.I.i32NotEqual)
            }

            else -> {
                throw UnsupportedOperationException("unhandled instruction: $instruction")
            }
        }
    }

    private fun compileDefineFunction(instruction: DefineFunction, context: WasmFunctionContext): WasmFunctionContext {
        val freeVariables = findFreeVariables(instruction)

        val paramBindings = mutableListOf<Pair<Int, String>>()

        fun compileParameter(parameter: DefineFunction.Parameter): WasmParam {
            val identifier = "param_${parameter.name.value}"
            paramBindings.add(parameter.variableId to identifier)
            return WasmParam(identifier = identifier, type = WasmData.genericValueType)
        }

        val positionalParams = instruction.positionalParameters.map(::compileParameter)
        val namedParams = instruction.namedParameters.map { parameter ->
            parameter.name to compileParameter(parameter)
        }

        val (context2, closure) = WasmClosures.compileCreate(
            functionName = instruction.name,
            freeVariables = freeVariables,
            compileBody = { currentContext ->
                compileInstructions(
                    instruction.bodyInstructions,
                    context = currentContext.bindVariables(paramBindings),
                )
            },
            positionalParams = positionalParams,
            namedParams = namedParams,
            context = context,
        )
        return context2.addInstruction(Wasm.I.localGet(closure))
    }

    private fun compileDefineShape(
        instruction: DefineShape,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        val tagValue = instruction.rawShapeType.tagValue
        val layout = WasmObjects.shapeTypeLayout(instruction.metaType)

        val (context2, shape) = malloc("shape", layout, context)

        val (context4, constructorTableIndex) = WasmShapes.compileConstructor(instruction.rawShapeType, context2)
        val context5 = context4.addInstruction(
            Wasm.I.i32Store(
                address = Wasm.I.localGet(shape),
                offset = layout.closureOffset,
                value = Wasm.I.i32Const(constructorTableIndex),
            )
        )
        val context6 = if (tagValue == null) {
            context5
        } else {
            context5.addInstruction(
                Wasm.I.i32Store(
                    address = Wasm.I.localGet(shape),
                    offset = layout.tagValueOffset,
                    value = Wasm.I.i32Const(WasmConstValue.TagValue(tagValue)),
                )
            )
        }

        val (context7, nameMemoryIndex) = context6.addSizedStaticUtf8String(instruction.rawShapeType.name.value)

        val context8 = context7.addInstruction(Wasm.I.i32Store(
            address = Wasm.I.localGet(shape),
            offset = layout.fieldOffset(Identifier("name")),
            value = Wasm.I.i32Const(nameMemoryIndex),
        ))

        return context8.addInstruction(Wasm.I.localGet(shape))
    }

    private fun compileCall(
        positionalArgumentCount: Int,
        namedArgumentNames: List<Identifier>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val (context2, positionalArgLocals) = (0 until positionalArgumentCount)
            .fold(Pair(context, persistentListOf<String>())) { (currentContext, args), argIndex ->
                val (currentContext2, arg) = currentContext.addLocal("arg_$argIndex")
                Pair(currentContext2, args.add(arg))
            }

        val (context3, namedArgLocals) = namedArgumentNames
            .fold(Pair(context2, persistentListOf<String>())) { (currentContext, args), argName ->
                val (currentContext2, arg) = currentContext.addLocal("arg_${argName.value}")
                Pair(currentContext2, args.add(arg))
            }

        val context4 = (positionalArgLocals + namedArgLocals).foldRight(context3) { local, currentContext ->
            currentContext.addInstruction(Wasm.I.localSet(local))
        }

        val (context5, callee) = context4.addLocal("callee")
        val context6 = context5.addInstruction(Wasm.I.localSet(callee))

        return WasmClosures.compileCall(
            closurePointer = Wasm.I.localGet(callee),
            positionalArguments = positionalArgLocals.map(Wasm.I::localGet),
            namedArguments = namedArgumentNames.zip(namedArgLocals.map(Wasm.I::localGet)),
            context = context6,
        )
    }

    private fun compileModuleInitCall(moduleName: ModuleName, context: WasmFunctionContext): WasmFunctionContext {
        val initFunctionIdentifier = WasmNaming.moduleInit(moduleName)
        return context
            .addDependency(moduleName)
            .addInstruction(Wasm.I.call(
                identifier = initFunctionIdentifier,
                args = listOf(),
            ))
    }

    private fun moduleStore(
        moduleName: ModuleName,
        exports: List<Pair<Identifier, WasmInstruction.Folded>>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val moduleType = moduleSet.moduleType(moduleName)!!
        return WasmModules.compileStore(
            moduleName = moduleName,
            moduleType = moduleType,
            exports = exports,
            context = context,
        )
    }

    private fun addJumpIfFalse(label: Int, joinLabel: Int, context: WasmFunctionContext): WasmFunctionContext {
        return context
            .addInstruction(Wasm.I.if_(results = listOf(Wasm.T.i32)))
            .addOnLabel(label, Wasm.I.else_)
            .addOnLabel(joinLabel, Wasm.I.end)
    }

    private fun addBoolNot(context: WasmFunctionContext): WasmFunctionContext {
        val (context2, local) = context.addLocal()

        return context2
            .addInstruction(Wasm.I.localSet(local))
            .addInstruction(Wasm.I.i32Sub(Wasm.I.i32Const(1), Wasm.I.localGet(local)))
    }
}
