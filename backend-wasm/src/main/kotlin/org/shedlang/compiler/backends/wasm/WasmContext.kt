package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.*
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.NullSource
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
import org.shedlang.compiler.types.TagValue
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class WasmGlobalContext private constructor(
    private val imports: PersistentList<WasmImport>,
    private val globals: PersistentList<Pair<WasmGlobal, WasmInstruction.Folded?>>,
    private val functions: PersistentList<WasmFunction>,
    private val table: PersistentList<String>,
    private val staticData: PersistentList<Pair<WasmDataSegmentKey, WasmStaticData>>,
    private val moduleNames: PersistentSet<ModuleName>,
    private val dependencies: PersistentSet<ModuleName>,
    private val tagValues: PersistentSet<TagValue>,
) {
    companion object {
        fun initial() = WasmGlobalContext(
            imports = persistentListOf(),
            globals = persistentListOf(),
            functions = persistentListOf(),
            table = persistentListOf(),
            staticData = persistentListOf(),
            moduleNames = persistentSetOf(),
            dependencies = persistentSetOf(),
            tagValues = persistentSetOf(),
        )

        fun merge(contexts: List<WasmGlobalContext>): WasmGlobalContext {
            return WasmGlobalContext(
                imports = contexts.flatMap { context -> context.imports }.toPersistentList(),
                globals = contexts.flatMap { context -> context.globals }.toPersistentList(),
                functions = contexts.flatMap { context -> context.functions }.toPersistentList(),
                table = contexts.flatMap { context -> context.table }.toPersistentList(),
                staticData = contexts.flatMap { context -> context.staticData }.toPersistentList(),
                moduleNames = contexts.flatMap { context -> context.moduleNames }.toPersistentSet(),
                dependencies = contexts.flatMap { context -> context.dependencies }.toPersistentSet(),
                tagValues = contexts.flatMap { context -> context.tagValues }.toPersistentSet(),
            )
        }
    }

    fun merge(other: WasmGlobalContext): WasmGlobalContext {
        return WasmGlobalContext(
            imports = imports.addAll(other.imports),
            globals = globals.addAll(other.globals),
            functions = functions.addAll(other.functions),
            table = table.addAll(other.table),
            staticData = staticData.addAll(other.staticData),
            moduleNames = moduleNames.addAll(other.moduleNames),
            dependencies = dependencies.addAll(other.dependencies),
            tagValues = tagValues.addAll(other.tagValues),
        )
    }

    fun toModule(): WasmCompilationResult {
        val imports = listOf(
            Wasi.importFdWrite(),
            Wasi.importProcExit(),
        ) + imports

        var size = 0
        val dataSegments = mutableListOf<WasmDataSegment>()
        val startInstructions = mutableListOf<WasmInstruction>()

        fun align(alignment: Int) {
            size = roundUp(size, alignment)
        }

        for ((staticDataKey, data) in staticData) {
            align(data.alignment)

            val (dataSegmentSize, dataSegmentBytes) = when (data) {
                is WasmStaticData.I32 -> {
                    val bytes = if (data.initial == null) {
                        null
                    } else {
                         ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.initial).array();

                    }
                    Pair(4, bytes)
                }
                is WasmStaticData.SizedUtf8String -> {
                    val utf8Bytes = data.value.toByteArray(Charsets.UTF_8)

                    val bytes = ByteBuffer.allocate(4 + utf8Bytes.size)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(utf8Bytes.size)
                        .put(utf8Bytes)
                        .array()

                    Pair(bytes.size, bytes)
                }
                is WasmStaticData.Bytes -> {
                    Pair(data.size, null)
                }
            }
            val dataSegment = WasmDataSegment(
                key = staticDataKey,
                offset = size,
                alignment = data.alignment,
                size = dataSegmentSize,
                bytes = dataSegmentBytes,
                name = data.name,
            )
            size += dataSegment.size
            dataSegments.add(dataSegment)
        }

        for ((global, value) in globals) {
            if (value != null) {
                startInstructions.add(Wasm.I.globalSet(global.identifier, value))
            }
        }

        val moduleFunctions = listOf(
            Wasm.function(
                identifier = WasmNaming.funcStartIdentifier,
                body = startInstructions,
            ),
        ) + functions

        val importFunctionTypes = imports
            .map { import -> import.descriptor }
            .filterIsInstance<WasmImportDescriptor.Function>()
            .map { descriptor -> descriptor.type() }
        val definedFunctionTypes = moduleFunctions.map { function -> function.type() }
        val functionTypes = (importFunctionTypes + definedFunctionTypes).distinct()

        val tagValuesToInt = tagValues.mapIndexed { tagValueIndex, tagValue ->
            tagValue to tagValueIndex
        }.toMap()

        val module = Wasm.module(
            dataSegments = dataSegments,
            globals = globals.map { (global, _) -> global },
            functions = moduleFunctions,
            imports = imports,
            memoryPageCount = divideRoundingUp(size, WASM_PAGE_SIZE),
            start = WasmNaming.funcStartIdentifier,
            table = table,
            types = functionTypes,
        )

        return WasmCompilationResult(module = module, tagValuesToInt = tagValuesToInt)
    }

    fun addImport(import: WasmImport): WasmGlobalContext {
        return copy(imports = imports.add(import))
    }

    fun addTableEntry(identifier: String): WasmGlobalContext {
        return copy(table = table.add(identifier))
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
        return copy(functions = functions.add(function))
    }

    fun addFunction(function: WasmFunction): WasmGlobalContext {
        return copy(functions = functions.add(function), table = table.add(function.identifier))
    }

    fun addStaticI32(initial: Int? = null): Pair<WasmGlobalContext, WasmDataSegmentKey> {
        return addStaticData(WasmStaticData.I32(initial = initial))
    }

    fun addSizedStaticUtf8String(value: String): Pair<WasmGlobalContext, WasmDataSegmentKey> {
        return addStaticData(WasmStaticData.SizedUtf8String(value))
    }

    fun addStaticData(size: Int, alignment: Int, name: String? = null): Pair<WasmGlobalContext, WasmDataSegmentKey> {
        return addStaticData(WasmStaticData.Bytes(size = size, alignment = alignment, name = name))
    }

    private fun addStaticData(data: WasmStaticData): Pair<WasmGlobalContext, WasmDataSegmentKey> {
        val ref = nextWasmDataSegmentKey()
        val newContext = copy(
            staticData = staticData.add(Pair(ref, data)),
        )
        return Pair(newContext, ref)
    }

    fun addDependency(dependency: ModuleName): WasmGlobalContext {
        return copy(dependencies = dependencies.add(dependency))
    }

    fun addModuleName(moduleName: List<Identifier>): WasmGlobalContext {
        return copy(moduleNames = moduleNames.add(moduleName))
    }

    fun missingDependencies(): PersistentSet<ModuleName> {
        return dependencies.removeAll(moduleNames)
    }

    fun compileTagValue(tagValue: TagValue): WasmGlobalContext {
        return copy(tagValues = tagValues.add(tagValue))
    }
}

private const val initialLocalIndex = 1

internal data class WasmFunctionContext(
    private val instructions: PersistentList<WasmInstruction>,
    private val nextLocalIndex: Int,
    private val locals: PersistentList<String>,
    private val variableIdToLocal: PersistentMap<Int, String>,
    private val onLocalStore: PersistentMap<Int, PersistentList<(String) -> WasmInstruction>>,
    private val onLabel: PersistentMap<Int, PersistentList<WasmInstruction>>,
    private val globalContext: WasmGlobalContext,
) {
    companion object {
        fun initial() = WasmFunctionContext(
            instructions = persistentListOf(),
            nextLocalIndex = initialLocalIndex,
            locals = persistentListOf(),
            onLocalStore = persistentMapOf(),
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

    fun addInstructions(newInstructions: List<WasmInstruction>): WasmFunctionContext {
        return copy(
            instructions = instructions.addAll(newInstructions),
        )
    }

    fun addInstructions(newInstructions: WasmInstructionSequence): WasmFunctionContext {
        return copy(
            instructions = instructions.addAll(newInstructions.toList()),
        )
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

    fun variableToStoredLocal(variableId: Int): String {
        val existingLocal = variableIdToLocal[variableId]
        if (existingLocal == null) {
            throw CompilerError("variable is not set", source = NullSource)
        } else {
            return existingLocal
        }
    }

    fun variableToStoredLocal(
        variableId: Int,
        onStore: (String) -> WasmInstruction,
    ): WasmFunctionContext {
        val existingLocal = variableIdToLocal[variableId]
        if (existingLocal == null) {
            return addOnLocalStore(variableId, onStore)
        } else {
            return addInstruction(onStore(existingLocal))
        }
    }

    private fun addOnLocalStore(variableId: Int, onStore: (String) -> WasmInstruction): WasmFunctionContext {
        return copy(onLocalStore = onLocalStore.put(variableId, onLocalStore.getOrDefault(variableId, persistentListOf()).add(onStore)))
    }

    fun onLocalStore(variableId: Int): WasmFunctionContext {
        val instructions = onLocalStore.getOrDefault(variableId, persistentListOf())
            .map { onStore -> onStore(variableToStoredLocal(variableId)) }
        return addInstructions(instructions)
    }

    fun addOnLabel(label: Int, instruction: WasmInstruction): WasmFunctionContext {
        return copy(onLabel = onLabel.put(label, onLabel(label).add(instruction)))
    }

    fun onLabel(label: Int): PersistentList<WasmInstruction> {
        return onLabel.getOrDefault(label, persistentListOf())
    }

    fun addImport(import: WasmImport): WasmFunctionContext {
        val newGlobalContext = globalContext.addImport(import)
        return copy(globalContext = newGlobalContext)
    }

    fun addTableEntry(identifier: String): WasmFunctionContext {
        val newGlobalContext = globalContext.addTableEntry(identifier)
        return copy(globalContext = newGlobalContext)
    }

    fun addImmutableGlobal(identifier: String, type: WasmValueType, value: WasmInstruction.Folded): WasmFunctionContext {
        val newGlobalContext = globalContext.addImmutableGlobal(identifier = identifier, type = type, value = value)
        return copy(globalContext = newGlobalContext)
    }

    fun addMutableGlobal(identifier: String, type: WasmValueType, initial: WasmInstruction.Folded): WasmFunctionContext {
        val newGlobalContext = globalContext.addMutableGlobal(identifier = identifier, type = type, initial = initial)
        return copy(globalContext = newGlobalContext)
    }

    fun addSizedStaticUtf8String(value: String): Pair<WasmFunctionContext, WasmDataSegmentKey> {
        val (newGlobalContext, index) = globalContext.addSizedStaticUtf8String(value)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun addStaticI32(value: Int): Pair<WasmFunctionContext, WasmDataSegmentKey> {
        val (newGlobalContext, index) = globalContext.addStaticI32(value)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun addStaticData(size: Int, alignment: Int, name: String? = null): Pair<WasmFunctionContext, WasmDataSegmentKey> {
        val (newGlobalContext, index) = globalContext.addStaticData(size = size, alignment = alignment, name = name)
        return Pair(copy(globalContext = newGlobalContext), index)
    }

    fun addDependency(moduleName: ModuleName): WasmFunctionContext {
        return copy(globalContext = globalContext.addDependency(dependency = moduleName))
    }

    fun compileTagValue(tagValue: TagValue): WasmFunctionContext {
        val newGlobalContext = globalContext.compileTagValue(tagValue)
        return copy(globalContext = newGlobalContext)
    }

    fun mergeGlobalContext(globalContext: WasmGlobalContext): WasmFunctionContext {
        return copy(globalContext = this.globalContext.merge(globalContext))
    }

    fun toStaticFunctionInGlobalContext(
        identifier: String,
        export: Boolean = false,
        params: List<WasmParam> = listOf(),
        results: List<WasmValueType> = listOf(),
    ): WasmGlobalContext {
        val function = toFunction(
            identifier = identifier,
            export = export,
            params = params,
            results = results,
        )

        return globalContext.addStaticFunction(function)
    }

    fun toFunctionInGlobalContext(
        identifier: String,
        export: Boolean = false,
        params: List<WasmParam> = listOf(),
        results: List<WasmValueType> = listOf(),
    ): WasmGlobalContext {
        val function = toFunction(
            identifier = identifier,
            export = export,
            params = params,
            results = results,
        )

        return globalContext.addFunction(function)
    }

    private fun toFunction(
        identifier: String,
        export: Boolean = false,
        params: List<WasmParam> = listOf(),
        results: List<WasmValueType> = listOf(),
    ): WasmFunction {
        return Wasm.function(
            identifier = identifier,
            export = export,
            params = params,
            results = results,
            locals = locals.map { local -> Wasm.local(local, WasmData.genericValueType) },
            body = instructions,
        )
    }
}

private sealed class WasmStaticData {
    abstract val alignment: Int
    abstract val name: String?

    data class I32(val initial: Int?): WasmStaticData() {
        override val alignment: Int
            get() = 4

        override val name: String?
            get() = null
    }

    data class SizedUtf8String(val value: String): WasmStaticData() {
        override val alignment: Int
            get() = 4

        override val name: String?
            get() = null
    }

    data class Bytes(
        val size: Int,
        override val alignment: Int,
        override val name: String?
    ): WasmStaticData()
}
