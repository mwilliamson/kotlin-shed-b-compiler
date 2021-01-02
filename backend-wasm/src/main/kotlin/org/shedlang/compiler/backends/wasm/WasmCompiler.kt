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
import org.shedlang.compiler.backends.wasm.wasm.Wat
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.Type
import java.lang.UnsupportedOperationException

// TODO: Int implementation should be big integers, not i32
internal class WasmCompiler(private val image: Image, private val moduleSet: ModuleSet) {
    class CompilationResult(val wat: String)

    fun compile(mainModule: ModuleName): CompilationResult {
        val moduleType = moduleSet.moduleType(mainModule)!!
        val startFunctionContext = WasmFunctionContext.initial()
            .let { compileModuleInitialisation(mainModule, context = it) }
            .addInstruction(WasmModules.compileLoad(mainModule))
            .addInstruction(WasmObjects.compileFieldLoad(objectType = moduleType, fieldName = Identifier("main")))
            .let { compileCall(positionalArgumentCount = 0, namedArgumentNames = listOf(), context = it) }
            .addInstruction(Wasi.callProcExit())

        val globalContext = compileRuntime().merge(startFunctionContext.globalContext)
        val boundGlobalContext = globalContext.bind()

        val module = Wasm.module(
            types = boundGlobalContext.types,
            imports = listOf(
                Wasi.importFdWrite(),
                Wasi.importProcExit(),
            ),
            globals = boundGlobalContext.globals,
            memoryPageCount = boundGlobalContext.pageCount,
            start = WasmNaming.funcStartIdentifier,
            dataSegments = boundGlobalContext.dataSegments,
            table = boundGlobalContext.table,
            functions = listOf(
                Wasm.function(
                    identifier = WasmNaming.funcStartIdentifier,
                    body = boundGlobalContext.startInstructions,
                ),
                startFunctionContext.toFunction(
                    identifier = WasmNaming.funcMainIdentifier,
                    exportName = "_start",
                ),
            ) + boundGlobalContext.functions,
        )

        val wat = Wat(lateIndices = boundGlobalContext.lateIndices).serialise(module)
        return CompilationResult(wat = wat)
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
                val (context2, identifier) = context.variableToLocal(
                    variableId = instruction.variableId,
                    name = instruction.name,
                )
                return context2.addInstruction(Wasm.I.localGet(identifier))
            }

            is LocalStore -> {
                val (context2, identifier) = context.variableToLocal(
                    variableId = instruction.variableId,
                    name = instruction.name,
                )
                return context2.addInstruction(Wasm.I.localSet(identifier))
            }

            is ModuleInit -> {
                return compileModuleInitialisation(instruction.moduleName, context)
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
                        val bytes = value.value.toByteArray(Charsets.UTF_8)

                        val (context2, memoryIndex) = context.addStaticI32(bytes.size)
                        val (context3, _) = context2.addStaticUtf8String(value.value)

                        return context3.addInstruction(Wasm.I.i32Const(memoryIndex))
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

    private fun compileModuleInitialisation(moduleName: ModuleName, context: WasmFunctionContext): WasmFunctionContext {
        // TODO: check whether module has already been compiled
        val moduleInit = image.moduleInitialisation(moduleName)
        if (moduleInit != null) {
            // TODO: check whether module has already been initialised
            val initContext = compileInstructions(moduleInit, WasmFunctionContext.initial())
            val initFunctionIdentifier = WasmNaming.moduleInit(moduleName)
            val initFunction = initContext.toFunction(identifier = initFunctionIdentifier)
            val (context2, _) = context.addFunction(initFunction)
            return context2.mergeGlobalContext(initContext)
                .addInstruction(Wasm.I.call(
                    identifier = initFunctionIdentifier,
                    args = listOf(),
                ))
        } else if (moduleName == listOf(Identifier("Core"), Identifier("Io"))) {
            // TODO: implement this properly!

            val (context2, closure) = WasmClosures.compileCreate(
                // TODO: build identifiers in WasmNaming
                functionName = "shed_module__core_io__print",
                freeVariables = listOf(),
                positionalParams = listOf(WasmParam("value", type = WasmData.genericValueType)),
                namedParams = listOf(),
                compileBody = { currentContext -> currentContext
                    .addInstruction(Wasm.I.call(
                        identifier = WasmNaming.Runtime.print,
                        args = listOf(Wasm.I.localGet("value")),
                    ))
                    .addInstruction(WasmData.unitValue)
                },
                context,
            )

            return moduleStore(
                moduleName = moduleName,
                exports = listOf(
                    Pair(Identifier("print"), Wasm.I.localGet(closure)),
                ),
                context = context2,
            )
        } else {
            throw CompilerError(message = "module not found: ${formatModuleName(moduleName)}", source = NullSource)
        }
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
