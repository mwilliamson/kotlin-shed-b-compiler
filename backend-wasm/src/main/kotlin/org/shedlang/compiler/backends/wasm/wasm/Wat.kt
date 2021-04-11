package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.backends.wasm.LateIndex
import org.shedlang.compiler.backends.wasm.StaticDataKey
import org.shedlang.compiler.nullableToList
import java.lang.StringBuilder
import java.lang.UnsupportedOperationException
import java.math.BigInteger
import java.util.*

internal class Wat(
    private val dataAddresses: Map<StaticDataKey, Int>,
    private val lateIndices: Map<LateIndex, Int>,
    private val symbolTable: WasmSymbolTable,
) {
    companion object {
        fun serialise(
            module: WasmModule,
            dataAddresses: Map<StaticDataKey, Int>,
            lateIndices: Map<LateIndex, Int>,
        ): String {
            return Wat(dataAddresses = dataAddresses, lateIndices = lateIndices, symbolTable = WasmSymbolTable.forModule(module))
                .serialise(module)
        }
    }

    fun serialise(module: WasmModule): String {
        return moduleToSExpression(module).serialise()
    }

    fun moduleToSExpression(module: WasmModule): SExpression {
        val typeDefinitions = module.types.map { type ->
            S.list(
                S.symbol("type"),
                S.identifier(funcTypeIdentifier(type)),
                S.list(
                    S.symbol("func"),
                    S.list(S.symbol("param")).addAll(valueTypesToSExpressions(type.params)),
                    S.list(S.symbol("result")).addAll(valueTypesToSExpressions(type.results)),
                ),
            )
        }

        val memoryExpression = if (module.memoryPageCount == null) {
            null
        } else {
            S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(module.memoryPageCount))
        }

        val startExpression = if (module.start == null) {
            null
        } else {
            S.list(S.symbol("start"), S.identifier(module.start))
        }

        val table = if (module.table.isEmpty()) {
            listOf()
        } else {
            listOf(
                S.list(
                    S.symbol("table"),
                    S.symbol("funcref"),
                    S.list(S.symbol("elem")).addAll(module.table.map(S::identifier)),
                ),
            )
        }

        return S.list(
            S.symbol("module"),
            S.formatBreak,
            *typeDefinitions.toTypedArray(),
            *module.imports.map { import -> importToSExpression(import) }.toTypedArray(),
            *module.globals.map { global -> globalToSExpression(global) }.toTypedArray(),
            *memoryExpression.nullableToList().toTypedArray(),
            *module.dataSegments.map { dataSegment -> dataSegmentToSExpression(dataSegment) }.toTypedArray(),
            *startExpression.nullableToList().toTypedArray(),
            *module.functions.map { function -> functionToSExpression(function) }.toTypedArray(),
        ).addAll(table)
    }

    fun importToSExpression(import: WasmImport): SExpression {
        val descriptor = import.descriptor

        return S.list(
            S.symbol("import"),
            S.string(import.moduleName),
            S.string(import.entityName),
            when (descriptor) {
                is WasmImportDescriptor.Function ->
                    S.list(
                        S.symbol("func"),
                        S.identifier(import.identifier),
                        S.list(S.symbol("param")).addAll(valueTypesToSExpressions(descriptor.params)),
                        S.list(S.symbol("result")).addAll(valueTypesToSExpressions(descriptor.results)),
                    )
                else -> TODO()
            },
        )
    }

    fun valueTypesToSExpressions(types: List<WasmValueType>): List<SExpression> {
        return types.map { type -> valueTypeToSExpression(type) }
    }

    fun valueTypeToSExpression(type: WasmValueType): SExpression {
        val name = when (type) {
            WasmValueType.i32 -> "i32"
        }
        return S.symbol(name)
    }

    fun globalToSExpression(global: WasmGlobal): SExpression {
        val type = if (global.mutable) {
            S.list(S.symbol("mut"), valueTypeToSExpression(global.type))
        } else {
            valueTypeToSExpression(global.type)
        }

        return S.list(
            S.symbol("global"),
            S.identifier(global.identifier),
            type,
            instructionToSExpression(global.value),
        )
    }

    fun dataSegmentToSExpression(dataSegment: WasmDataSegment): SExpression {
        return S.list(
            S.symbol("data"),
            instructionToSExpression(Wasm.I.i32Const(dataSegment.offset)),
            S.bytes(dataSegment.bytes),
        )
    }

    fun functionToSExpression(function: WasmFunction): SExpression {
        val exportExpressions = if (function.export) {
            listOf(S.list(S.symbol("export"), S.string(function.identifier)))
        } else {
            listOf()
        }

        return S.list(
            S.symbol("func"),
            S.identifier(function.identifier),
            *exportExpressions.toTypedArray(),
            *function.params.map { param -> paramToSExpression(param) }.toTypedArray(),
            S.list(S.symbol("result")).addAll(valueTypesToSExpressions(function.results)),
            S.formatBreak,
            *function.locals.map { local -> localToSExpression(local) }.toTypedArray(),
            *function.body.map { instruction -> instructionToSExpression(instruction) }.toTypedArray(),
        )
    }

    fun paramToSExpression(param: WasmParam): SExpression {
        return S.list(S.symbol("param"), S.identifier(param.identifier), valueTypeToSExpression(param.type))
    }

    fun localToSExpression(param: WasmLocal): SExpression {
        return S.list(S.symbol("local"), S.identifier(param.identifier), valueTypeToSExpression(param.type))
    }

    fun instructionToSExpression(instruction: WasmInstruction): SExpression {
        return when (instruction) {
            is WasmInstruction.Branch -> S.elements(S.symbol("br"), S.identifier(instruction.identifier))
            is WasmInstruction.Call -> S.elements(S.symbol("call"), S.identifier(instruction.identifier))
            is WasmInstruction.CallIndirect -> S.elements(S.symbol("call_indirect"), S.list(
                S.symbol("type"),
                S.identifier(funcTypeIdentifier(instruction.type)),
            ))
            is WasmInstruction.Drop -> S.symbol("drop")
            is WasmInstruction.Else -> S.symbol("else")
            is WasmInstruction.End -> S.symbol("end")
            is WasmInstruction.GlobalSet -> S.elements(S.symbol("global.set"), S.identifier(instruction.identifier))
            is WasmInstruction.I32Add -> S.symbol("i32.add")
            is WasmInstruction.I32And -> S.symbol("i32.and")
            is WasmInstruction.I32DivideSigned -> S.symbol("i32.div_s")
            is WasmInstruction.I32DivideUnsigned -> S.symbol("i32.div_u")
            is WasmInstruction.I32Equals -> S.symbol("i32.eq")
            is WasmInstruction.I32GreaterThanSigned -> S.symbol("i32.gt_s")
            is WasmInstruction.I32GreaterThanUnsigned -> S.symbol("i32.gt_u")
            is WasmInstruction.I32GreaterThanOrEqualSigned -> S.symbol("i32.ge_s")
            is WasmInstruction.I32GreaterThanOrEqualUnsigned -> S.symbol("i32.ge_u")
            is WasmInstruction.I32LessThanSigned -> S.symbol("i32.lt_s")
            is WasmInstruction.I32LessThanUnsigned -> S.symbol("i32.lt_u")
            is WasmInstruction.I32LessThanOrEqualSigned -> S.symbol("i32.le_s")
            is WasmInstruction.I32LessThanOrEqualUnsigned -> S.symbol("i32.le_u")
            is WasmInstruction.I32Load -> S.elements(S.symbol("i32.load"), *memarg(offset = instruction.offset, alignment = instruction.alignment))
            is WasmInstruction.I32Load8Unsigned -> S.symbol("i32.load8_u")
            is WasmInstruction.I32Multiply -> S.symbol("i32.mul")
            is WasmInstruction.I32NotEqual -> S.symbol("i32.ne")
            is WasmInstruction.I32Store -> S.symbol("i32.store")
            is WasmInstruction.I32Store8 -> S.symbol("i32.store8")
            is WasmInstruction.I32Sub -> S.symbol("i32.sub")
            is WasmInstruction.If -> S.elements(
                S.symbol("if"),
                S.list(S.symbol("result")).addAll(valueTypesToSExpressions(instruction.results)),
            )
            is WasmInstruction.LocalSet -> S.elements(S.symbol("local.set"), S.identifier(instruction.identifier))
            is WasmInstruction.Loop -> S.elements(
                S.symbol("loop"),
                S.identifier(instruction.identifier),
                S.list(S.symbol("result")).addAll(valueTypesToSExpressions(instruction.results)),
            )
            is WasmInstruction.MemoryGrow -> S.symbol("memory.grow")

            is WasmInstruction.Folded.Call -> S.list(
                S.symbol("call"),
                S.identifier(instruction.identifier),
                S.elements(instruction.args.map(::instructionToSExpression))
            )
            is WasmInstruction.Folded.CallIndirect ->
                S.list(
                    S.symbol("call_indirect"),
                    S.list(
                        S.symbol("type"),
                        S.identifier(funcTypeIdentifier(instruction.type)),
                    ),
                )
                    .addAll(instruction.args.map { arg -> instructionToSExpression(arg) })
                    .add(instructionToSExpression(instruction.tableIndex))
            is WasmInstruction.Folded.Drop -> S.list(
                S.symbol("drop"),
                instructionToSExpression(instruction.value),
            )
            is WasmInstruction.Folded.GlobalGet -> S.list(
                S.symbol("global.get"),
                S.identifier(instruction.identifier),
            )
            is WasmInstruction.Folded.GlobalSet -> S.list(
                S.symbol("global.set"),
                S.identifier(instruction.identifier),
                instructionToSExpression(instruction.value),
            )
            is WasmInstruction.Folded.I32Add -> S.list(
                S.symbol("i32.add"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32And -> S.list(
                S.symbol("i32.and"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32Const -> S.list(
                S.symbol("i32.const"),
                S.int(constValueToInt(instruction.value)),
            )
            is WasmInstruction.Folded.I32DivideSigned -> S.list(
                S.symbol("i32.div_s"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32DivideUnsigned -> S.list(
                S.symbol("i32.div_u"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32Equals -> S.list(
                S.symbol("i32.eq"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32GreaterThanOrEqualUnsigned -> S.list(
                S.symbol("i32.ge_u"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32LessThanOrEqualUnsigned -> S.list(
                S.symbol("i32.le_u"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32Load -> S.list(
                S.symbol("i32.load"),
                *memarg(offset = instruction.offset, alignment = instruction.alignment),
                instructionToSExpression(instruction.address),
            )
            is WasmInstruction.Folded.I32Multiply -> S.list(
                S.symbol("i32.mul"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32NotEqual -> S.list(
                S.symbol("i32.ne"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32Store -> S.list(
                S.symbol("i32.store"),
                *memarg(offset = instruction.offset, alignment = instruction.alignment),
                instructionToSExpression(instruction.address),
                instructionToSExpression(instruction.value),
            )
            is WasmInstruction.Folded.I32Sub -> S.list(
                S.symbol("i32.sub"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.If -> S.list(
                S.symbol("if"),
                S.list(S.symbol("result")).addAll(valueTypesToSExpressions(instruction.results)),
                instructionToSExpression(instruction.condition),
                S.formatBreak,
                S.list(S.symbol("then"), S.formatBreak).addAll(instruction.ifTrue.map(::instructionToSExpression)),
                S.list(S.symbol("else"), S.formatBreak).addAll(instruction.ifFalse.map(::instructionToSExpression)),
            )
            is WasmInstruction.Folded.LocalGet -> S.list(
                S.symbol("local.get"),
                S.identifier(instruction.identifier),
            )
            is WasmInstruction.Folded.LocalSet -> S.list(
                S.symbol("local.set"),
                S.identifier(instruction.identifier),
                instructionToSExpression(instruction.value),
            )
            is WasmInstruction.Folded.MemoryGrow -> S.list(
                S.symbol("memory.grow"),
                instructionToSExpression(instruction.delta)
            )
            is WasmInstruction.Folded.MemorySize -> S.list(S.symbol("memory.size"))
        }
    }

    private fun memarg(offset: Int, alignment: Int?): Array<SSymbol> {
        return listOf(
            *(if (offset == 0) listOf() else listOf(S.symbol("offset=${offset}"))).toTypedArray(),
            *(if (alignment == null) listOf() else listOf(S.symbol("align=${alignment}"))).toTypedArray(),
        ).toTypedArray()
    }

    private fun constValueToInt(value: WasmConstValue): Int {
        return when (value) {
            is WasmConstValue.DataIndex -> dataAddresses[value.key]!!
            is WasmConstValue.I32 -> value.value
            is WasmConstValue.LateIndex -> lateIndices[value.ref]!!
            is WasmConstValue.TableEntryIndex -> symbolTable.tableEntryIndex(value.identifier)
        }
    }

    private fun funcTypeIdentifier(type: WasmFuncType): String {
        val parts = mutableListOf<String>()
        parts.add("functype")
        parts.add(type.params.size.toString())
        for (param in type.params) {
            parts.add(param.name)
        }
        parts.add(type.results.size.toString())
        for (result in type.results) {
            parts.add(result.name)
        }
        return parts.joinToString("_")
    }
}

internal object S {
    fun elements(elements: List<SExpression>) = SElements(elements)
    fun elements(vararg elements: SExpression) = SElements(elements.toList())
    val formatBreak = SFormatBreak
    fun int(value: Int) = SInt(value.toBigInteger())
    fun int(value: Long) = SInt(value.toBigInteger())
    fun string(value: String) = SString(value)
    fun bytes(value: ByteArray) = SBytes(value)
    fun symbol(value: String) = SSymbol(value)
    fun identifier(value: String) = SIdentifier(value)
    fun list(vararg elements: SExpression) = SList(elements.toList())
}

internal interface SExpression {
    fun serialise(): String
}

internal object SFormatBreak : SExpression {
    override fun serialise(): String {
        throw UnsupportedOperationException()
    }
}

internal data class SInt(val value: BigInteger): SExpression {
    override fun serialise(): String {
        return value.toString()
    }
}

internal data class SString(val value: String): SExpression {
    override fun serialise(): String {
        // TODO: handle escaping
        return "\"${value.replace("\n", "\\n")}\""
    }
}

internal data class SBytes(val value: ByteArray): SExpression {
    override fun serialise(): String {
        val encodedBytes = value
            .joinToString("") { byte -> "\\%02X".format(byte) }
        return "\"$encodedBytes\""
    }

    override fun equals(other: Any?): Boolean {
        return other is SBytes && this.value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

internal data class SSymbol(val value: String): SExpression {
    override fun serialise(): String {
        // TODO: handle escaping
        return value
    }
}

internal data class SIdentifier(val value: String): SExpression {
    override fun serialise(): String {
        // TODO: handle escaping
        return "\$" + value
    }
}

internal data class SList(val elements: List<SExpression>) : SExpression {
    override fun serialise(): String {
        val builder = StringBuilder()
        builder.append("(")

        var separator = " "
        var end = ""

        elements.forEachIndexed { elementIndex, element ->
            if (element == SFormatBreak) {
                separator = "\n  "
                end = "\n"
            } else {
                if (elementIndex > 0) {
                    builder.append(separator)
                }
                builder.append(element.serialise().replace("\n", "\n  "))
            }
        }

        builder.append(end)
        builder.append(")")
        return builder.toString()
    }

    fun add(newElement: SExpression): SList {
        return addAll(listOf(newElement))
    }

    fun addAll(newElements: List<SExpression>): SList {
        return SList(elements = elements + newElements)
    }
}

internal data class SElements(private val elements: List<SExpression>): SExpression {
    override fun serialise(): String {
        return elements.joinToString(" ") { element -> element.serialise() }
    }

}
