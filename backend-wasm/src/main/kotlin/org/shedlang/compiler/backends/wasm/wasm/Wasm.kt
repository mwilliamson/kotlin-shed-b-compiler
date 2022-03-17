package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.types.TagValue

internal object Wasm {
    fun module(
        types: List<WasmFuncType> = listOf(),
        imports: List<WasmImport> = listOf(),
        globals: List<WasmGlobal> = listOf(),
        tags: List<WasmTag> = listOf(),
        memoryPageCount: Int? = null,
        dataSegments: List<WasmDataSegment> = listOf(),
        start: String? = null,
        functions: List<WasmFunction> = listOf(),
        table: List<String> = listOf(),
    ) = WasmModule(
        types = types,
        imports = imports,
        globals = globals,
        tags = tags,
        memoryPageCount = memoryPageCount,
        dataSegments = dataSegments,
        start = start,
        functions = functions,
        table = table,
    )

    fun importFunction(
        moduleName: String,
        entityName: String,
        identifier: String,
        params: List<WasmValueType>,
        results: List<WasmValueType>
    ) = WasmImport(
        moduleName = moduleName,
        entityName = entityName,
        identifier = identifier,
        descriptor = WasmImportDescriptor.Function(params = params, results = results),
    )

    fun importMemory(
        moduleName: String,
        entityName: String,
        identifier: String,
        limits: WasmLimits,
    ) = WasmImport(
        moduleName = moduleName,
        entityName = entityName,
        identifier = identifier,
        descriptor = WasmImportDescriptor.Memory(limits = limits),
    )

    fun importTable(
        moduleName: String,
        entityName: String,
        identifier: String,
        limits: WasmLimits,
    ) = WasmImport(
        moduleName = moduleName,
        entityName = entityName,
        identifier = identifier,
        descriptor = WasmImportDescriptor.Table(limits = limits),
    )

    fun dataSegment(
        offset: Int,
        alignment: Int,
        bytes: ByteArray,
        name: String? = null,
    ) = WasmDataSegment(
        key = nextWasmDataSegmentKey(),
        offset = offset,
        alignment = alignment,
        size = bytes.size,
        bytes = bytes,
        name = name,
    )

    fun dataSegmentZeroed(
        offset: Int,
        alignment: Int,
        size: Int,
        name: String? = null,
    ) = WasmDataSegment(
        key = nextWasmDataSegmentKey(),
        offset = offset,
        alignment = alignment,
        size = size,
        bytes = null,
        name = name,
    )

    fun function(
        identifier: String,
        export: Boolean = false,
        params: List<WasmParam> = listOf(),
        locals: List<WasmLocal> = listOf(),
        results: List<WasmValueType> = listOf(),
        body: List<WasmInstruction>,
    ) = WasmFunction(
        identifier = identifier,
        export = export,
        params = params,
        locals = locals,
        results = results,
        body = body,
    )

    fun global(
        identifier: String,
        mutable: Boolean,
        type: WasmValueType,
        value: WasmInstruction.Folded,
    ) = WasmGlobal(
        identifier = identifier,
        mutable = mutable,
        type = type,
        value = value
    )

    fun tag(identifier: String, type: WasmFuncType) = WasmTag(identifier = identifier, type = type)

    fun param(identifier: String, type: WasmValueType) = WasmParam(identifier = identifier, type = type)

    fun local(identifier: String, type: WasmValueType) = WasmLocal(identifier = identifier, type = type)

    object T {
        val i32 = WasmValueType.i32

        fun funcType(params: List<WasmValueType>, results: List<WasmValueType>): WasmFuncType {
            return WasmFuncType(params = params, results = results)
        }
    }

    object I {
        fun branch(identifier: String): WasmInstruction {
            return WasmInstruction.Branch(identifier = identifier)
        }

        fun call(identifier: String): WasmInstruction {
            return WasmInstruction.Call(identifier = identifier)
        }

        fun call(identifier: String, args: List<WasmInstruction.Folded>): WasmInstruction.Folded {
            return WasmInstruction.Folded.Call(identifier = identifier, args = args)
        }

        fun callIndirect(type: WasmFuncType, tableIndex: WasmInstruction.Folded, args: List<WasmInstruction.Folded>): WasmInstruction.Folded {
            return WasmInstruction.Folded.CallIndirect(type = type, tableIndex = tableIndex, args = args)
        }

        fun catch(tagName: String): WasmInstruction {
            return WasmInstruction.Catch(tagName)
        }

        val drop = WasmInstruction.Drop

        fun drop(value: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.Drop(value = value)
        }

        val else_ = WasmInstruction.Else

        val end = WasmInstruction.End

        fun globalGet(identifier: String): WasmInstruction.Folded {
            return WasmInstruction.Folded.GlobalGet(identifier)
        }

        fun globalSet(identifier: String, value: WasmInstruction.Folded): WasmInstruction {
            return WasmInstruction.Folded.GlobalSet(identifier, value)
        }

        val i32Add = WasmInstruction.I32Add
        val i32And = WasmInstruction.I32And
        val i32DivideUnsigned = WasmInstruction.I32DivideUnsigned
        val i32Equals = WasmInstruction.I32Equals
        val i32GreaterThanSigned = WasmInstruction.I32GreaterThanSigned
        val i32GreaterThanUnsigned = WasmInstruction.I32GreaterThanUnsigned
        val i32GreaterThanOrEqualSigned = WasmInstruction.I32GreaterThanOrEqualSigned
        val i32GreaterThanOrEqualUnsigned = WasmInstruction.I32GreaterThanOrEqualUnsigned
        val i32LessThanSigned = WasmInstruction.I32LessThanSigned
        val i32LessThanUnsigned = WasmInstruction.I32LessThanUnsigned
        val i32LessThanOrEqualSigned = WasmInstruction.I32LessThanOrEqualSigned
        val i32LessThanOrEqualUnsigned = WasmInstruction.I32LessThanOrEqualUnsigned
        val i32Load8Unsigned = WasmInstruction.I32Load8Unsigned
        val i32Multiply = WasmInstruction.I32Multiply
        val i32NotEqual = WasmInstruction.I32NotEqual
        val i32Store8 = WasmInstruction.I32Store8
        val i32Sub = WasmInstruction.I32Sub


        fun i32Add(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Add(left = left, right = right)
        }

        fun i32And(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32And(left = left, right = right)
        }

        fun i32Const(value: Int): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Const(WasmConstValue.I32(value))
        }

        fun i32Const(value: WasmDataSegmentKey): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Const(WasmConstValue.DataIndexByKey(value))
        }

        fun i32Const(value: TagValue): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Const(WasmConstValue.TagValue(value))
        }

        fun i32Const(value: WasmConstValue): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Const(value)
        }

        fun i32DivideSigned(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32DivideSigned(left = left, right = right)
        }

        fun i32DivideUnsigned(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32DivideUnsigned(left = left, right = right)
        }

        fun i32Equals(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Equals(left = left, right = right)
        }

        fun i32GreaterThanOrEqualUnsigned(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32GreaterThanOrEqualUnsigned(left = left, right = right)
        }

        fun i32LessThanOrEqualUnsigned(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32LessThanOrEqualUnsigned(left = left, right = right)
        }

        fun i32Load(offset: Int, alignment: Int): WasmInstruction {
            return WasmInstruction.I32Load(offset = offset, alignment = alignment)
        }

        // TODO: make alignment required
        fun i32Load(address: WasmInstruction.Folded, offset: Int = 0, alignment: Int? = null): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Load(offset = offset, alignment = alignment, address = address)
        }

        fun i32Multiply(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Multiply(left = left, right = right)
        }

        fun i32NotEqual(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32NotEqual(left = left, right = right)
        }

        // TODO: make alignment required
        fun i32Store(offset: Int = 0, alignment: Int? = null): WasmInstruction {
            return WasmInstruction.I32Store(offset = offset, alignment = alignment)
        }

        // TODO: make alignment required
        fun i32Store(address: WasmInstruction.Folded, value: WasmInstruction.Folded, offset: Int = 0, alignment: Int? = null): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Store(offset = offset, alignment = alignment, address = address, value = value)
        }

        fun i32Sub(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Sub(left = left, right = right)
        }

        fun if_(results: List<WasmValueType> = listOf()): WasmInstruction {
            return WasmInstruction.If(results = results)
        }

        fun if_(
            results: List<WasmValueType> = listOf(),
            condition: WasmInstruction.Folded,
            ifTrue: List<WasmInstruction>,
            ifFalse: List<WasmInstruction> = listOf(),
        ): WasmInstruction.Folded {
            return WasmInstruction.Folded.If(results = results, condition = condition, ifTrue = ifTrue, ifFalse = ifFalse)
        }

        fun localGet(identifier: String): WasmInstruction.Folded {
            return WasmInstruction.Folded.LocalGet(identifier)
        }

        fun localSet(identifier: String): WasmInstruction {
            return WasmInstruction.LocalSet(identifier)
        }

        fun localSet(identifier: String, value: WasmInstruction.Folded): WasmInstruction {
            return WasmInstruction.Folded.LocalSet(identifier = identifier, value = value)
        }

        fun loop(identifier: String, results: List<WasmValueType> = listOf()): WasmInstruction {
            return WasmInstruction.Loop(identifier = identifier, results = results)
        }

        val memoryGrow = WasmInstruction.MemoryGrow

        fun memoryGrow(delta: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.MemoryGrow(delta = delta)
        }

        val memorySize = WasmInstruction.Folded.MemorySize

        fun throw_(identifier: String): WasmInstruction {
            return WasmInstruction.Throw(identifier)
        }

        fun try_(type: WasmValueType): WasmInstruction {
            return WasmInstruction.Try(type)
        }
    }
}

internal enum class WasmValueType {
    i32
}

internal class WasmModule(
    val types: List<WasmFuncType>,
    val imports: List<WasmImport>,
    val globals: List<WasmGlobal>,
    val tags: List<WasmTag>,
    val memoryPageCount: Int?,
    val dataSegments: List<WasmDataSegment>,
    val start: String?,
    val functions: List<WasmFunction>,
    val table: List<String>,
)

internal data class WasmFuncType(val params: List<WasmValueType>, val results: List<WasmValueType>)

internal class WasmImport(
    val moduleName: String,
    val entityName: String,
    val identifier: String,
    val descriptor: WasmImportDescriptor,
)

internal sealed class WasmImportDescriptor {
    class Function(val params: List<WasmValueType>, val results: List<WasmValueType>): WasmImportDescriptor() {
        fun type(): WasmFuncType {
            return Wasm.T.funcType(params = params, results = results)
        }
    }

    class Memory(val limits: WasmLimits): WasmImportDescriptor()
    class Table(val limits: WasmLimits): WasmImportDescriptor()
}

internal class WasmGlobal(val identifier: String, val mutable: Boolean, val type: WasmValueType, val value: WasmInstruction.Folded)

internal class WasmTag(val identifier: String, val type: WasmFuncType)

private var nextWasmDataSegmentKey = 1

internal fun nextWasmDataSegmentKey() = WasmDataSegmentKey(value = nextWasmDataSegmentKey++)

internal data class WasmDataSegmentKey(private val value: Int)

internal class WasmDataSegment(
    val key: WasmDataSegmentKey,
    val offset: Int,
    val alignment: Int,
    val size: Int,
    val bytes: ByteArray?,
    val name: String?,
)

internal class WasmFunction(
    val identifier: String,
    val export: Boolean,
    val params: List<WasmParam>,
    val locals: List<WasmLocal>,
    val results: List<WasmValueType>,
    val body: List<WasmInstruction>,
) {
    fun type() = WasmFuncType(params = params.map { param -> param.type }, results = results)
}

internal class WasmParam(val identifier: String, val type: WasmValueType)

internal class WasmLocal(val identifier: String, val type: WasmValueType)

internal interface WasmInstructionSequence {
    fun toList(): List<WasmInstruction>
}

internal sealed class WasmInstruction: WasmInstructionSequence {
    override fun toList(): List<WasmInstruction> {
        return listOf(this)
    }

    class Branch(val identifier: String): WasmInstruction()

    class Call(val identifier: String): WasmInstruction()
    class CallIndirect(val type: WasmFuncType): WasmInstruction()

    class Catch(val name: String): WasmInstruction()

    object Drop: WasmInstruction()

    object Else: WasmInstruction()

    object End: WasmInstruction()

    class GlobalSet(val identifier: String): WasmInstruction()

    object I32Add: WasmInstruction()
    object I32And: WasmInstruction()
    object I32DivideSigned: WasmInstruction()
    object I32DivideUnsigned: WasmInstruction()
    object I32Equals: WasmInstruction()
    object I32GreaterThanSigned: WasmInstruction()
    object I32GreaterThanUnsigned: WasmInstruction()
    object I32GreaterThanOrEqualSigned: WasmInstruction()
    object I32GreaterThanOrEqualUnsigned: WasmInstruction()
    object I32LessThanSigned: WasmInstruction()
    object I32LessThanUnsigned: WasmInstruction()
    object I32LessThanOrEqualSigned: WasmInstruction()
    object I32LessThanOrEqualUnsigned: WasmInstruction()
    class  I32Load(val offset: Int, val alignment: Int?): WasmInstruction()
    object I32Load8Unsigned: WasmInstruction()
    object I32Multiply: WasmInstruction()
    object I32NotEqual: WasmInstruction()
    class I32Store(val alignment: Int?, val offset: Int): WasmInstruction()
    object I32Store8: WasmInstruction()
    object I32Sub: WasmInstruction()

    class If(val results: List<WasmValueType>): WasmInstruction()

    class LocalSet(val identifier: String): WasmInstruction()

    class Loop(val identifier: String, val results: List<WasmValueType>): WasmInstruction()

    object MemoryGrow: WasmInstruction()

    class Throw(val identifier: String): WasmInstruction()

    class Try(val type: WasmValueType): WasmInstruction()

    interface Unfoldable {
        fun unfold(): List<WasmInstruction>
    }

    sealed class Folded: WasmInstruction() {
        class Call(val identifier: String, val args: List<Folded>): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return args + listOf(WasmInstruction.Call(identifier = identifier))
            }
        }

        class CallIndirect(val type: WasmFuncType, val tableIndex: Folded, val args: List<Folded>): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return args + listOf(tableIndex) + listOf(WasmInstruction.CallIndirect(type = type))
            }
        }

        class Drop(val value: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(value, WasmInstruction.Drop)
            }
        }

        class GlobalGet(val identifier: String): Folded()

        class GlobalSet(val identifier: String, val value: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(value, WasmInstruction.GlobalSet(identifier = identifier))
            }
        }

        class I32Add(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32Add)
            }
        }

        class I32And(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32And)
            }
        }

        class I32Const(val value: WasmConstValue): Folded()

        class I32DivideSigned(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32DivideSigned)
            }
        }

        class I32DivideUnsigned(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32DivideUnsigned)
            }
        }

        class I32Equals(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32Equals)
            }
        }

        class I32GreaterThanOrEqualUnsigned(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32GreaterThanOrEqualUnsigned)
            }
        }

        class I32LessThanOrEqualUnsigned(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32LessThanOrEqualUnsigned)
            }
        }

        class I32Load(val offset: Int, val alignment: Int?, val address: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(address, WasmInstruction.I32Load(offset = offset, alignment = alignment))
            }
        }

        class I32Multiply(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32Multiply)
            }
        }

        class I32NotEqual(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32NotEqual)
            }
        }

        class I32Store(val alignment: Int?, val offset: Int, val address: Folded, val value: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(address, value, WasmInstruction.I32Store(alignment = alignment, offset = offset))
            }
        }

        class I32Sub(val left: Folded, val right: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(left, right, WasmInstruction.I32Sub)
            }
        }

        class If(
            val results: List<WasmValueType>,
            val condition: Folded,
            val ifTrue: List<WasmInstruction>,
            val ifFalse: List<WasmInstruction>,
        ): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(condition, WasmInstruction.If(results = results)) +
                    ifTrue + listOf(WasmInstruction.Else) + ifFalse + listOf(WasmInstruction.End)
            }
        }

        class LocalGet(val identifier: String): Folded()

        class LocalSet(val identifier: String, val value: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(value, WasmInstruction.LocalSet(identifier = identifier))
            }
        }

        class MemoryGrow(val delta: Folded): Folded(), Unfoldable {
            override fun unfold(): List<WasmInstruction> {
                return listOf(delta, WasmInstruction.MemoryGrow)
            }
        }

        object MemorySize: Folded()
    }
}

internal sealed class WasmConstValue {
    data class DataIndexByKey(val key: WasmDataSegmentKey): WasmConstValue()
    data class DataIndexByName(val identifier: String): WasmConstValue()
    data class I32(val value: Int): WasmConstValue()
    data class TableEntryIndex(val identifier: String): WasmConstValue()
    data class TagValue(val tagValue: org.shedlang.compiler.types.TagValue): WasmConstValue()
}

internal class WasmLimits(val min: Int, val max: Int?)

const val WASM_PAGE_SIZE = 65536
