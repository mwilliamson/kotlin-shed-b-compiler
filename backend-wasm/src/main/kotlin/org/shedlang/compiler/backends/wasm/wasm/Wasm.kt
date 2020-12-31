package org.shedlang.compiler.backends.wasm.wasm

internal object Wasm {
    fun module(
        imports: List<WasmImport> = listOf(),
        memoryPageCount: Int = 0,
        dataSegments: List<WasmDataSegment> = listOf(),
        start: String? = null,
        functions: List<WasmFunction> = listOf(),
    ) = WasmModule(
        imports = imports,
        memoryPageCount = memoryPageCount,
        dataSegments = dataSegments,
        start = start,
        functions = functions,
    )

    fun importFunction(
        moduleName: String,
        entityName: String,
        identifier: String,
        params: List<WasmType>,
        results: List<WasmType>
    ) = WasmImport(
        moduleName = moduleName,
        entityName = entityName,
        identifier = identifier,
        descriptor = WasmImportDescriptor.Function(params = params, results = results),
    )

    fun dataSegment(
        offset: Int,
        bytes: ByteArray,
    ) = WasmDataSegment(
        offset = offset,
        bytes = bytes,
    )

    fun function(
        identifier: String,
        exportName: String? = null,
        params: List<WasmParam> = listOf(),
        locals: List<WasmLocal> = listOf(),
        results: List<WasmType> = listOf(),
        body: List<WasmInstruction>,
    ) = WasmFunction(
        identifier = identifier,
        exportName = exportName,
        params = params,
        locals = locals,
        results = results,
        body = body,
    )

    fun param(identifier: String, type: WasmType) = WasmParam(identifier = identifier, type = type)

    fun local(identifier: String, type: WasmType) = WasmLocal(identifier = identifier, type = type)

    fun instructions(vararg instructions: WasmInstruction): List<WasmInstruction> {
        return instructions.toList()
    }

    object T {
        val i32 = WasmScalarType("i32")
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

        val drop = WasmInstruction.Drop

        fun drop(value: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.Drop(value = value)
        }

        val else_ = WasmInstruction.Else

        val end = WasmInstruction.End

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
        val i32Load = WasmInstruction.I32Load
        val i32Load8Unsigned = WasmInstruction.I32Load8Unsigned
        val i32Multiply = WasmInstruction.I32Multiply
        val i32NotEqual = WasmInstruction.I32NotEqual
        val i32Store = WasmInstruction.I32Store
        val i32Store8 = WasmInstruction.I32Store8
        val i32Sub = WasmInstruction.I32Sub


        fun i32Add(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Add(left = left, right = right)
        }

        fun i32And(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32And(left = left, right = right)
        }

        fun i32Const(value: Int): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Const(value)
        }

        fun i32DivideUnsigned(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32DivideUnsigned(left = left, right = right)
        }

        fun i32LessThanOrEqualUnsigned(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32LessThanOrEqualUnsigned(left = left, right = right)
        }

        fun i32Load(address: Int): WasmInstruction.Folded {
            return i32Load(i32Const(address))
        }

        fun i32Load(address: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Load(address = address)
        }

        fun i32Multiply(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Multiply(left = left, right = right)
        }

        fun i32Store(address: Int, value: Int): WasmInstruction.Folded {
            return i32Store(i32Const(address), i32Const(value))
        }

        fun i32Store(address: WasmInstruction.Folded, value: WasmInstruction.Folded, offset: Int = 0, alignment: Int? = null): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Store(offset = offset, alignment = alignment, address = address, value = value)
        }

        fun i32Sub(left: WasmInstruction.Folded, right: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.I32Sub(left = left, right = right)
        }

        fun if_(results: List<WasmType>): WasmInstruction {
            return WasmInstruction.If(results = results)
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

        fun loop(identifier: String, results: List<WasmType> = listOf()): WasmInstruction {
            return WasmInstruction.Loop(identifier = identifier, results = results)
        }

        val memoryGrow = WasmInstruction.MemoryGrow

        fun memoryGrow(delta: WasmInstruction.Folded): WasmInstruction.Folded {
            return WasmInstruction.Folded.MemoryGrow(delta = delta)
        }

        val memorySize = WasmInstruction.Folded.MemorySize
    }
}

internal interface WasmType

internal class WasmScalarType(val name: String): WasmType

internal class WasmModule(
    val imports: List<WasmImport>,
    val memoryPageCount: Int,
    val dataSegments: List<WasmDataSegment>,
    val start: String?,
    val functions: List<WasmFunction>,
)

internal class WasmImport(
    val moduleName: String,
    val entityName: String,
    val identifier: String,
    val descriptor: WasmImportDescriptor,
)

internal sealed class WasmImportDescriptor {
    class Function(val params: List<WasmType>, val results: List<WasmType>): WasmImportDescriptor()
}

internal class WasmDataSegment(val offset: Int, val bytes: ByteArray)

internal class WasmFunction(
    val identifier: String,
    val exportName: String?,
    val params: List<WasmParam>,
    val locals: List<WasmLocal>,
    val results: List<WasmType>,
    val body: List<WasmInstruction>,
)

internal class WasmParam(val identifier: String, val type: WasmType)

internal class WasmLocal(val identifier: String, val type: WasmType)

internal interface WasmInstructionSequence {
    fun toList(): List<WasmInstruction>
}

internal sealed class WasmInstruction: WasmInstructionSequence {
    override fun toList(): List<WasmInstruction> {
        return listOf(this)
    }

    class Branch(val identifier: String): WasmInstruction()

    class Call(val identifier: String): WasmInstruction()

    object Drop: WasmInstruction()

    object Else: WasmInstruction()

    object End: WasmInstruction()

    object I32Add: WasmInstruction()
    object I32And: WasmInstruction()
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
    object I32Load: WasmInstruction()
    object I32Load8Unsigned: WasmInstruction()
    object I32Multiply: WasmInstruction()
    object I32NotEqual: WasmInstruction()
    object I32Store: WasmInstruction()
    object I32Store8: WasmInstruction()
    object I32Sub: WasmInstruction()

    class If(val results: List<WasmType>): WasmInstruction()

    class LocalSet(val identifier: String): WasmInstruction()

    class Loop(val identifier: String, val results: List<WasmType>): WasmInstruction()

    object MemoryGrow: WasmInstruction()

    sealed class Folded: WasmInstruction() {
        class Call(val identifier: String, val args: List<Folded>): Folded()
        class Drop(val value: Folded): Folded()
        class I32Add(val left: Folded, val right: Folded): Folded()
        class I32And(val left: Folded, val right: Folded): Folded()
        class I32Const(val value: Int): Folded()
        class I32DivideUnsigned(val left: Folded, val right: Folded): Folded()
        class I32LessThanOrEqualUnsigned(val left: Folded, val right: Folded): Folded()
        class I32Load(val address: Folded): Folded()
        class I32Multiply(val left: Folded, val right: Folded): Folded()
        class I32Store(val alignment: Int?, val offset: Int, val address: Folded, val value: Folded): Folded()
        class I32Sub(val left: Folded, val right: Folded): Folded()
        class LocalGet(val identifier: String): Folded()
        class LocalSet(val identifier: String, val value: Folded): Folded()
        class MemoryGrow(val delta: Folded): Folded()
        object MemorySize: Folded()
    }
}
