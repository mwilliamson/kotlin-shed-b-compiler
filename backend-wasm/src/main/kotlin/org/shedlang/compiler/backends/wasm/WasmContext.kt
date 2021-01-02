package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmDataSegment
import org.shedlang.compiler.backends.wasm.wasm.WasmFuncType
import org.shedlang.compiler.backends.wasm.wasm.WasmFunction
import org.shedlang.compiler.backends.wasm.wasm.WasmGlobal
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.backends.wasm.wasm.WasmInstructionSequence
import org.shedlang.compiler.backends.wasm.wasm.WasmParam
import org.shedlang.compiler.backends.wasm.wasm.WasmValueType
import org.shedlang.compiler.stackir.divideRoundingUp
import org.shedlang.compiler.stackir.roundUp

private var nextLateIndexKey = 1

private fun nextLateIndex() = LateIndex(key = nextLateIndexKey++)

internal data class LateIndex(private val key: Int)

internal data class WasmGlobalContext private constructor(
    private val globals: PersistentList<Pair<WasmGlobal, WasmInstruction.Folded?>>,
    private val functions: PersistentList<Pair<LateIndex?, WasmFunction>>,
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
            if (lateIndex != null) {
                lateIndices[lateIndex] = table.size
                table.add(function.identifier)
            }
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

    fun addStaticFunction(function: WasmFunction): WasmGlobalContext {
        return copy(functions = functions.add(Pair(null, function)))
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

private sealed class WasmStaticData(val alignment: Int?) {
    data class I32(val initial: WasmInstruction.Folded?): WasmStaticData(alignment = 4)
    data class Utf8String(val value: String): WasmStaticData(alignment = null)
    data class Bytes(val size: Int, private val bytesAlignment: Int?): WasmStaticData(alignment = bytesAlignment)
}
