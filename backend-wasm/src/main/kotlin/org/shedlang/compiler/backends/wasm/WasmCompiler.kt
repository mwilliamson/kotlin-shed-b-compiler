package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.*
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasi
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.Wat
import org.shedlang.compiler.stackir.*
import java.lang.UnsupportedOperationException

// TODO: Int implementation should be big integers, not i32
internal class WasmCompiler(private val image: Image, private val moduleSet: ModuleSet) {
    class CompilationResult(val wat: String)

    fun compile(mainModule: ModuleName): CompilationResult {
        val messageOffset = 8
        val message = "Hello, world!\n"
        val wasm = Wasm.module(
            imports = listOf(
                Wasi.importFdWrite("fd_write"),
            ),
            memoryPageCount = 1,
            dataSegments = listOf(
                Wasm.dataSegment(offset = messageOffset, bytes = message.toByteArray()),
            ),
            start = "start",
            functions = listOf(
                Wasm.function(
                    identifier = "start",
                    body = Wasm.instructions(
                        Wasm.I.i32Store(0, messageOffset),
                        Wasm.I.i32Store(4, message.length),
                    ),
                ),
                Wasm.function(
                    identifier = "main",
                    exportName = "_start",
                    body = Wasm.instructions(
                        Wasi.callFdWrite(
                            identifier = "fd_write",
                            fileDescriptor = Wasi.stdout,
                            iovs = Wasm.I.i32Const(0),
                            iovsLen = Wasm.I.i32Const(1),
                            nwritten = Wasm.I.i32Const(8 + message.length),
                        ),
                        Wasm.I.drop,
                    ),
                ),
            ),
        )

        val wat = Wat(lateIndices = persistentMapOf()).serialise(wasm)
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
                val argumentCount = instruction.positionalArgumentCount + instruction.namedArgumentNames.size
                val wasmFuncType = WasmFuncType(
                    params = (0 until argumentCount).map { WasmData.genericValueType },
                    results = listOf(WasmData.genericValueType),
                )

                val positionalArgLocals = (0 until instruction.positionalArgumentCount).map { argIndex -> "arg_$argIndex" }
                val namedArgLocals = instruction.namedArgumentNames.map { argName -> "arg_${argName.value}" }
                val argLocals = positionalArgLocals + namedArgLocals

                val (context2, reversedArgs) = argLocals
                    .foldRight(Pair(context, persistentListOf<String>())) { argLocal, (currentContext, args) ->
                        val (currentContext2, arg) = currentContext.addLocal(argLocal)
                        val currentContext3 = currentContext2.addInstruction(Wasm.I.localSet(arg))
                        Pair(currentContext3, args.add(arg))
                    }

                val (context3, callee) = context2.addLocal("callee")
                val context4 = context3.addInstruction(Wasm.I.localSet(callee))

                return context4.addInstruction(Wasm.I.callIndirect(
                    type = wasmFuncType.identifier(),
                    tableIndex = Wasm.I.localGet(callee),
                    args = reversedArgs.reversed().map { arg -> Wasm.I.localGet(arg) }
                ))
            }

            is DefineFunction -> {
                // TODO: uniquify name
                val functionName = instruction.name

                val params = mutableListOf<WasmParam>()
                val paramBindings = mutableListOf<Pair<Int, String>>()
                // TODO: Sort named parameters
                for (parameter in (instruction.positionalParameters + instruction.namedParameters)) {
                    val identifier = "param_${parameter.name.value}"
                    params.add(WasmParam(identifier = identifier, type = WasmData.genericValueType))
                    paramBindings.add(parameter.variableId to identifier)
                }
                val initialFunctionContext = WasmFunctionContext.initial().bindVariables(paramBindings)

                val functionContext = compileInstructions(
                    instruction.bodyInstructions,
                    context = initialFunctionContext,
                )

                val context2 = context.mergeGlobalContext(functionContext.globalContext)

                val (context3, functionIndex) = context2.addFunction(Wasm.function(
                    identifier = functionName,
                    params = params,
                    results = listOf(WasmData.genericValueType),
                    body = functionContext.instructions,
                ))
                return context3.addInstruction(Wasm.I.i32Const(functionIndex))
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
                        return context.addInstruction(Wasm.I.i32Const(0))
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
                return context.addInstruction(Wasm.I.call(WasmCoreNames.stringAdd))
            }

            is StringEquals -> {
                return context.addInstruction(Wasm.I.call(WasmCoreNames.stringEquals))
            }

            is StringNotEqual -> {
                return addBoolNot(context.addInstruction(Wasm.I.call(WasmCoreNames.stringEquals)))
            }

            is TupleAccess -> {
                return context
                    .addInstruction(Wasm.I.i32Const(instruction.elementIndex * WasmData.VALUE_SIZE))
                    .addInstruction(Wasm.I.i32Add)
                    .addInstruction(Wasm.I.i32Load)
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
    private val functions: PersistentList<Pair<LateIndex, WasmFunction>>,
    private val staticData: PersistentList<Pair<LateIndex, WasmStaticData>>,
) {
    companion object {
        fun initial() = WasmGlobalContext(
            functions = persistentListOf(),
            staticData = persistentListOf()
        )

        fun merge(contexts: List<WasmGlobalContext>): WasmGlobalContext {
            return WasmGlobalContext(
                functions = contexts.flatMap { context -> context.functions }.toPersistentList(),
                staticData = contexts.flatMap { context -> context.staticData }.toPersistentList(),
            )
        }
    }

    fun merge(other: WasmGlobalContext): WasmGlobalContext {
        return WasmGlobalContext(
            functions = functions.addAll(other.functions),
            staticData = staticData.addAll(other.staticData),
        )
    }

    class Bound(
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
            }
        }

        val boundFunctions = mutableListOf<WasmFunction>()
        val table = mutableListOf<String>()

        functions.forEachIndexed { tableIndex, (lateIndex, function) ->
            table.add(function.identifier)
            lateIndices[lateIndex] = tableIndex
            boundFunctions.add(function)
        }

        val functionTypes = boundFunctions.map { function -> function.type() }

        return Bound(
            pageCount = divideRoundingUp(size, WASM_PAGE_SIZE),
            dataSegments = dataSegments,
            startInstructions = startInstructions,
            functions = boundFunctions,
            table = table,
            types = functionTypes,
            lateIndices = lateIndices,
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
    internal val instructions: PersistentList<WasmInstruction>,
    private val nextLocalIndex: Int,
    internal val locals: PersistentList<String>,
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

    fun addStaticUtf8String(value: String): Pair<WasmFunctionContext, LateIndex> {
        val (newGlobalContext, index) = globalContext.addStaticUtf8String(value)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun addStaticI32(value: Int): Pair<WasmFunctionContext, LateIndex> {
        val (newGlobalContext, index) = globalContext.addStaticI32(value)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun mergeGlobalContext(globalContext: WasmGlobalContext): WasmFunctionContext {
        return copy(globalContext = this.globalContext.merge(globalContext))
    }
}
