package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.*
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.NullSource
import org.shedlang.compiler.ast.formatModuleName
import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasi
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.Wat
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.Type
import java.lang.Integer.max
import java.lang.UnsupportedOperationException

// TODO: Int implementation should be big integers, not i32
internal class WasmCompiler(private val image: Image, private val moduleSet: ModuleSet) {
    class CompilationResult(val wat: String)

    fun compile(mainModule: ModuleName): CompilationResult {
        val moduleType = moduleSet.moduleType(mainModule)!!
        val startFunctionContext = WasmFunctionContext.initial()
            .let { compileModuleInitialisation(mainModule, context = it) }
            .addInstruction(compileModuleLoad(mainModule))
            .addInstruction(compileFieldAccess(objectType = moduleType, fieldName = Identifier("main")))
            .let { compileCall(positionalArgumentCount = 0, namedArgumentNames = listOf(), context = it) }
            .addInstruction(Wasi.callProcExit())

        val (mallocGlobalContext, malloc) = generateMalloc()
        val (printGlobalContext, printFunc) = generatePrintFunc()
        val stringEqualsFunc = generateStringEqualsFunc()
        val stringAddFunc = generateStringAddFunc()
        val builtins = listOf(malloc, printFunc, stringAddFunc, stringEqualsFunc)
        val globalContext = WasmGlobalContext.merge(listOf(
            startFunctionContext.globalContext,
            mallocGlobalContext,
            printGlobalContext,
        ))
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
            ) + boundGlobalContext.functions + builtins,
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
                return context.addInstruction(compileFieldAccess(instruction.receiverType, instruction.fieldName))
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
                return context.addInstruction(compileModuleLoad(instruction.moduleName))
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
                return context.addInstruction(Wasm.I.i32Load(
                    offset = instruction.elementIndex * WasmData.VALUE_SIZE,
                    alignment = WasmData.VALUE_SIZE,
                ))
            }

            is TupleCreate -> {
                val (context2, tuplePointer) = context.addLocal("tuple")
                val (context3, element) = context2.addLocal("element")
                val context4 = context3.addInstruction(Wasm.I.localSet(
                    tuplePointer,
                    callMalloc(
                        size = Wasm.I.i32Const(WasmData.VALUE_SIZE * instruction.length),
                        alignment = Wasm.I.i32Const(WasmData.VALUE_SIZE),
                    ),
                ))

                val context5 = (instruction.length - 1 downTo 0).fold(context4) { currentContext, elementIndex ->
                    currentContext
                        .addInstruction(Wasm.I.localSet(element))
                        .addInstruction(Wasm.I.i32Store(
                            offset = elementIndex * WasmData.VALUE_SIZE,
                            alignment = WasmData.VALUE_SIZE,
                            address = Wasm.I.localGet(tuplePointer),
                            value = Wasm.I.localGet(element),
                        ))
                }

                return context5.addInstruction(Wasm.I.localGet(tuplePointer))
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

    private fun moduleStore(
        moduleName: ModuleName,
        exports: List<Pair<Identifier, WasmInstruction.Folded>>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val moduleType = moduleSet.moduleType(moduleName)!!

        val (context2, moduleValue) = context.addStaticData(
            size = exports.size * WasmData.VALUE_SIZE,
            alignment = WasmData.VALUE_SIZE,
        )

        val context3 = exports.fold(context2) { currentContext, (exportName, exportValue) ->
            currentContext.addInstruction(storeField(
                objectPointer = Wasm.I.i32Const(moduleValue),
                objectType = moduleType,
                fieldName = exportName,
                fieldValue = exportValue,
            ))
        }

        return context3.addImmutableGlobal(
            identifier = WasmNaming.moduleValue(moduleName),
            type = WasmData.moduleValuePointerType,
            value = Wasm.I.i32Const(moduleValue),
        )
    }

    private fun compileDefineFunction(instruction: DefineFunction, context: WasmFunctionContext): WasmFunctionContext {
        // TODO: uniquify name
        val functionName = instruction.name

        val freeVariables = findFreeVariables(instruction)

        val params = mutableListOf<WasmParam>()
        params.add(WasmParam("shed_closure", type = WasmData.closurePointerType))

        val paramBindings = mutableListOf<Pair<Int, String>>()
        for (parameter in (instruction.positionalParameters + instruction.namedParameters.sortedBy { parameter -> parameter.name })) {
            val identifier = "param_${parameter.name.value}"
            params.add(WasmParam(identifier = identifier, type = WasmData.genericValueType))
            paramBindings.add(parameter.variableId to identifier)
        }

        val functionContext1 = WasmFunctionContext.initial().bindVariables(paramBindings)

        val functionContext2 = freeVariables.foldIndexed(functionContext1) { freeVariableIndex, currentContext, freeVariable ->
            val (currentContext2, local) = currentContext.variableToLocal(freeVariable.variableId, freeVariable.name)

            currentContext2.addInstruction(Wasm.I.localSet(
                local,
                Wasm.I.i32Load(
                    address = Wasm.I.localGet("shed_closure"),
                    offset = WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariableIndex,
                    alignment = WasmData.closureAlignment,
                ),
            ))
        }

        val functionContext3 = compileInstructions(
            instruction.bodyInstructions,
            context = functionContext2,
        )

        val context2 = context.mergeGlobalContext(functionContext3.globalContext)

        val (context3, functionIndex) = context2.addFunction(functionContext3.toFunction(
            identifier = functionName,
            params = params,
            results = listOf(WasmData.genericValueType),
        ))
        val (context4, closure) = compileCreateClosure(
            functionIndex = Wasm.I.i32Const(functionIndex),
            freeVariables = freeVariables,
            context = context3,
        )
        return context4.addInstruction(Wasm.I.localGet(closure))
    }

    private fun compileCreateClosure(
        functionIndex: WasmInstruction.Folded,
        freeVariables: List<LocalLoad>,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, String> {
        val (context2, closure) = context.addLocal("closure")

        val alignment = max(WasmData.FUNCTION_POINTER_SIZE, WasmData.VALUE_SIZE)
        val context3 = context2.addInstruction(Wasm.I.localSet(
            closure,
            callMalloc(
                size = Wasm.I.i32Const(WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariables.size),
                alignment = Wasm.I.i32Const(alignment),
            ),
        ))

        val context4 = freeVariables.foldIndexed(context3) { freeVariableIndex, currentContext, freeVariable ->
            val (currentContext2, local) = currentContext.variableToLocal(freeVariable.variableId, freeVariable.name)

            currentContext2.addInstruction(Wasm.I.i32Store(
                address = Wasm.I.localGet(closure),
                offset = WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariableIndex,
                value = Wasm.I.localGet(local),
                alignment = alignment,
            ))
        }

        val context5 = context4.addInstruction(Wasm.I.i32Store(
            Wasm.I.localGet(closure),
            functionIndex,
        ))

        return Pair(context5, closure)
    }

    private fun compileCall(
        positionalArgumentCount: Int,
        namedArgumentNames: List<Identifier>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val argumentCount = positionalArgumentCount + namedArgumentNames.size
        val wasmFuncType = WasmFuncType(
            params = listOf(WasmData.functionPointerType) + (0 until argumentCount).map { WasmData.genericValueType },
            results = listOf(WasmData.genericValueType),
        )

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

        val sortedNamedArgLocals = namedArgumentNames.zip(namedArgLocals)
            .sortedBy { (argName, _) -> argName }
            .map { (_, local) -> local }
        val argLocals = positionalArgLocals + sortedNamedArgLocals

        return context6.addInstruction(Wasm.I.callIndirect(
            type = wasmFuncType.identifier(),
            tableIndex = Wasm.I.i32Load(Wasm.I.localGet(callee)),
            args = listOf(Wasm.I.localGet(callee)) + argLocals.map { argLocal -> Wasm.I.localGet(argLocal) },
        ))
    }

    private fun compileFieldAccess(objectType: Type, fieldName: Identifier): WasmInstruction {
        return Wasm.I.i32Load(
            offset = fieldOffset(
                objectType = objectType,
                fieldName = fieldName,
            ),
            alignment = WasmData.VALUE_SIZE,
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

            val (context2, printFunction) = context.addFunction(Wasm.function(
                // TODO: build identifiers in WasmNaming
                identifier = "shed_module__core_io__print",
                params = listOf(
                    WasmParam("shed_closure", type = WasmData.closurePointerType),
                    WasmParam("value", type = WasmData.genericValueType)
                ),
                results = listOf(WasmData.genericValueType),
                body = listOf(
                    Wasm.I.call(
                        identifier = WasmNaming.Runtime.print,
                        args = listOf(Wasm.I.localGet("value")),
                    ),
                    WasmData.unitValue,
                ),
            ))

            val (context3, closure) = compileCreateClosure(
                functionIndex = Wasm.I.i32Const(printFunction),
                freeVariables = listOf(),
                context2,
            )

            return moduleStore(
                moduleName = moduleName,
                exports = listOf(
                    Pair(Identifier("print"), Wasm.I.localGet(closure)),
                ),
                context = context3,
            )
        } else {
            throw CompilerError(message = "module not found: ${formatModuleName(moduleName)}", source = NullSource)
        }
    }

    private fun compileModuleLoad(moduleName: ModuleName) =
        Wasm.I.globalGet(WasmNaming.moduleValue(moduleName))

    private fun storeField(
        objectPointer: WasmInstruction.Folded,
        objectType: ModuleType,
        fieldName: Identifier,
        fieldValue: WasmInstruction.Folded,
    ): WasmInstruction.Folded {
        objectType.fields

        return Wasm.I.i32Store(
            address = objectPointer,
            offset = fieldOffset(objectType, fieldName),
            alignment = WasmData.VALUE_SIZE,
            value = fieldValue,
        )
    }

    private fun fieldOffset(objectType: Type, fieldName: Identifier) =
        fieldIndex(type = objectType, fieldName = fieldName) * WasmData.VALUE_SIZE

    private fun fieldIndex(type: Type, fieldName: Identifier): Int {
        if (type !is ModuleType) {
            throw UnsupportedOperationException()
        }
        val sortedFieldNames = type.fields.keys.sorted()
        val fieldIndex = sortedFieldNames.indexOf(fieldName)

        if (fieldIndex == -1) {
            // TODO: better exception
            throw Exception("field not found: ${fieldName.value}")
        } else {
            return fieldIndex
        }
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

private var nextLateIndexKey = 1

internal fun nextLateIndex() = LateIndex(key = nextLateIndexKey++)

internal data class LateIndex(private val key: Int)

internal data class WasmGlobalContext(
    private val globals: PersistentList<Pair<WasmGlobal, WasmInstruction.Folded?>>,
    private val functions: PersistentList<Pair<LateIndex, WasmFunction>>,
    private val staticData: PersistentList<Pair<LateIndex, WasmStaticData>>,
) {
    companion object {
        fun initial() = WasmGlobalContext(
            globals = persistentListOf(),
            functions = persistentListOf(),
            staticData = persistentListOf()
        )

        fun merge(contexts: List<WasmGlobalContext>): WasmGlobalContext {
            return WasmGlobalContext(
                globals = contexts.flatMap { context -> context.globals }.toPersistentList(),
                functions = contexts.flatMap { context -> context.functions }.toPersistentList(),
                staticData = contexts.flatMap { context -> context.staticData }.toPersistentList(),
            )
        }
    }

    fun merge(other: WasmGlobalContext): WasmGlobalContext {
        return WasmGlobalContext(
            globals = globals.addAll(other.globals),
            functions = functions.addAll(other.functions),
            staticData = staticData.addAll(other.staticData),
        )
    }

    class Bound(
        internal val globals: List<WasmGlobal>,
        internal val pageCount: Int,
        internal val dataSegments: List<WasmDataSegment>,
        internal val startInstructions: List<WasmInstruction>,
        internal val functions: List<WasmFunction>,
        internal val table: List<String>,
        internal val types: List<WasmFuncType>,
        internal val lateIndices: Map<LateIndex, Int>,
    )

    fun bind(): Bound {
        var size = 0
        val dataSegments = mutableListOf<WasmDataSegment>()
        val startInstructions = mutableListOf<WasmInstruction>()
        val lateIndices = mutableMapOf<LateIndex, Int>()

        fun align(alignment: Int) {
            size = roundUp(size, alignment)
        }

        for ((lateIndex, data) in staticData) {
            if (data.alignment != null) {
                align(data.alignment)
            }
            lateIndices[lateIndex] = size

            when (data) {
                is WasmStaticData.I32 -> {
                    if (data.initial != null) {
                        startInstructions.add(Wasm.I.i32Store(Wasm.I.i32Const(size), data.initial))
                    }
                    size += 4
                }
                is WasmStaticData.Utf8String -> {
                    val bytes = data.value.toByteArray(Charsets.UTF_8)
                    dataSegments.add(WasmDataSegment(offset = size, bytes = bytes))
                    size += bytes.size
                }
                is WasmStaticData.Bytes -> {
                    size += data.size
                }
            }
        }

        val boundFunctions = mutableListOf<WasmFunction>()
        val table = mutableListOf<String>()

        functions.forEachIndexed { tableIndex, (lateIndex, function) ->
            table.add(function.identifier)
            lateIndices[lateIndex] = tableIndex
            boundFunctions.add(function)
        }

        val functionTypes = boundFunctions.map { function -> function.type() }.distinct()

        for ((global, value) in globals) {
            if (value != null) {
                startInstructions.add(Wasm.I.globalSet(global.identifier, value))
            }
        }

        return Bound(
            globals = globals.map { (global, _) -> global },
            pageCount = divideRoundingUp(size, WASM_PAGE_SIZE),
            dataSegments = dataSegments,
            startInstructions = startInstructions,
            functions = boundFunctions,
            table = table,
            types = functionTypes,
            lateIndices = lateIndices,
        )
    }

    fun addMutableGlobal(identifier: String, type: WasmValueType, initial: WasmInstruction.Folded): WasmGlobalContext {
        return copy(
            globals = globals.add(Pair(
                WasmGlobal(identifier = identifier, mutable = true, type = type, value = Wasm.I.i32Const(0)),
                initial,
            )),
        )
    }

    fun addImmutableGlobal(identifier: String, type: WasmValueType, value: WasmInstruction.Folded): WasmGlobalContext {
        return copy(
            globals = globals.add(Pair(
                WasmGlobal(identifier = identifier, mutable = false, type = type, value = value),
                null,
            )),
        )
    }

    fun addFunction(function: WasmFunction): Pair<WasmGlobalContext, LateIndex> {
        val index = nextLateIndex()
        val newContext = copy(functions = functions.add(Pair(index, function)))
        return Pair(newContext, index)
    }

    fun addStaticI32(initial: Int) = addStaticI32(initial = Wasm.I.i32Const(initial))

    fun addStaticI32(initial: WasmInstruction.Folded? = null): Pair<WasmGlobalContext, LateIndex> {
        return addStaticData(WasmStaticData.I32(initial = initial))
    }

    fun addStaticUtf8String(value: String): Pair<WasmGlobalContext, LateIndex> {
        return addStaticData(WasmStaticData.Utf8String(value))
    }

    fun addStaticData(size: Int, alignment: Int): Pair<WasmGlobalContext, LateIndex> {
        return addStaticData(WasmStaticData.Bytes(size = size, bytesAlignment = alignment))
    }

    private fun addStaticData(data: WasmStaticData): Pair<WasmGlobalContext, LateIndex> {
        val ref = nextLateIndex()
        val newContext = copy(
            staticData = staticData.add(Pair(ref, data)),
        )
        return Pair(newContext, ref)
    }
}

private const val initialLocalIndex = 1

internal data class WasmFunctionContext(
    private val instructions: PersistentList<WasmInstruction>,
    private val nextLocalIndex: Int,
    private val locals: PersistentList<String>,
    private val variableIdToLocal: PersistentMap<Int, String>,
    private val onLabel: PersistentMap<Int, PersistentList<WasmInstruction>>,
    internal val globalContext: WasmGlobalContext,
) {
    companion object {
        fun initial() = WasmFunctionContext(
            instructions = persistentListOf(),
            nextLocalIndex = initialLocalIndex,
            locals = persistentListOf(),
            variableIdToLocal = persistentMapOf(),
            onLabel = persistentMapOf(),
            globalContext = WasmGlobalContext.initial(),
        )
    }

    fun addInstruction(instruction: WasmInstruction): WasmFunctionContext {
        return copy(
            instructions = instructions.add(instruction),
        )
    }

    fun addInstructions(newInstructions: WasmInstructionSequence): WasmFunctionContext {
        return copy(
            instructions = instructions.addAll(newInstructions.toList()),
        )
    }

    fun addFunction(function: WasmFunction): Pair<WasmFunctionContext, LateIndex> {
        val (newGlobalContext, functionIndex) = globalContext.addFunction(function)
        val newContext = copy(globalContext = newGlobalContext)
        return Pair(newContext, functionIndex)
    }

    fun addLocal(name: String = "temp"): Pair<WasmFunctionContext, String> {
        val local = "local_${name}_${nextLocalIndex}"
        val newContext = copy(locals = locals.add(local), nextLocalIndex = nextLocalIndex + 1)
        return Pair(newContext, local)
    }

    fun bindVariables(variables: List<Pair<Int, String>>): WasmFunctionContext {
        return copy(variableIdToLocal = variables.fold(
            variableIdToLocal,
            { acc, (variableId, name) -> acc.put(variableId, name) }
        ))
    }

    fun variableToLocal(variableId: Int, name: Identifier): Pair<WasmFunctionContext, String> {
        val existingLocal = variableIdToLocal[variableId]
        if (existingLocal == null) {
            val (context2, local) = addLocal(name.value)
            val newContext = context2.copy(variableIdToLocal = variableIdToLocal.put(variableId, local))
            return Pair(newContext, local)
        } else {
            return Pair(this, existingLocal)
        }
    }

    fun addOnLabel(label: Int, instruction: WasmInstruction): WasmFunctionContext {
        return copy(onLabel = onLabel.put(label, onLabel(label).add(instruction)))
    }

    fun onLabel(label: Int): PersistentList<WasmInstruction> {
        return onLabel.getOrDefault(label, persistentListOf())
    }

    fun addImmutableGlobal(identifier: String, type: WasmValueType, value: WasmInstruction.Folded): WasmFunctionContext {
        val newGlobalContext = globalContext.addImmutableGlobal(identifier = identifier, type = type, value = value)
        return copy(globalContext = newGlobalContext)
    }

    fun addStaticUtf8String(value: String): Pair<WasmFunctionContext, LateIndex> {
        val (newGlobalContext, index) = globalContext.addStaticUtf8String(value)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun addStaticI32(): Pair<WasmFunctionContext, LateIndex> {
        val (newGlobalContext, index) = globalContext.addStaticI32()
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun addStaticI32(value: Int): Pair<WasmFunctionContext, LateIndex> {
        val (newGlobalContext, index) = globalContext.addStaticI32(value)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun addStaticData(size: Int, alignment: Int): Pair<WasmFunctionContext, LateIndex> {
        val (newGlobalContext, index) = globalContext.addStaticData(size = size, alignment = alignment)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun mergeGlobalContext(context: WasmFunctionContext): WasmFunctionContext {
        return copy(globalContext = this.globalContext.merge(context.globalContext))
    }

    fun mergeGlobalContext(globalContext: WasmGlobalContext): WasmFunctionContext {
        return copy(globalContext = this.globalContext.merge(globalContext))
    }

    fun toFunction(
        identifier: String,
        exportName: String? = null,
        params: List<WasmParam> = listOf(),
        results: List<WasmValueType> = listOf(),
    ): WasmFunction {
        return Wasm.function(
            identifier = identifier,
            exportName = exportName,
            params = params,
            results = results,
            locals = locals.map { local -> Wasm.local(local, WasmData.genericValueType) },
            body = instructions,
        )
    }
}
