package org.shedlang.compiler.backends.wasm.wasm

import org.shedlang.compiler.nullableToList
import java.lang.StringBuilder
import java.lang.UnsupportedOperationException
import java.math.BigInteger

internal object Wat {
    fun serialise(module: WasmModule): String {
        return moduleToSExpression(module).serialise()
    }

    fun moduleToSExpression(module: WasmModule): SExpression {
        val startExpression = if (module.start == null) {
            null
        } else {
            S.list(S.symbol("start"), S.identifier(module.start))
        }

        return S.list(
            S.symbol("module"),
            S.formatBreak,
            *module.imports.map { import -> importToSExpression(import) }.toTypedArray(),
            S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(module.memoryPageCount)),
            *module.dataSegments.map { dataSegment -> dataSegmentToSExpression(dataSegment) }.toTypedArray(),
            *startExpression.nullableToList().toTypedArray(),
            *module.functions.map { function -> functionToSExpression(function) }.toTypedArray(),
        )
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
                        S.list(S.symbol("param"), typesToSExpressions(descriptor.params)),
                        S.list(S.symbol("result"), typesToSExpressions(descriptor.results)),
                    )
            },
        )
    }

    fun typesToSExpressions(types: List<WasmType>): SExpression {
        return S.elements(types.map { type -> typeToSExpression(type) })
    }

    fun typeToSExpression(type: WasmType): SExpression {
        return S.symbol((type as WasmScalarType).name)
    }

    fun dataSegmentToSExpression(dataSegment: WasmDataSegment): SExpression {
        return S.list(
            S.symbol("data"),
            instructionToSExpression(Wasm.I.i32Const(dataSegment.offset)),
            S.string(dataSegment.bytes.decodeToString()),
        )
    }

    fun functionToSExpression(function: WasmFunction): SExpression {
        val exportExpressions = if (function.exportName == null) {
            listOf()
        } else {
            listOf(S.list(S.symbol("export"), S.string(function.exportName)))
        }

        return S.list(
            S.symbol("func"),
            S.identifier(function.identifier),
            *exportExpressions.toTypedArray(),
            *function.params.map { param -> paramToSExpression(param) }.toTypedArray(),
            S.list(S.symbol("result"), typesToSExpressions(function.results)),
            S.formatBreak,
            *function.locals.map { local -> localToSExpression(local) }.toTypedArray(),
            *function.body.map { instruction -> instructionToSExpression(instruction) }.toTypedArray(),
        )
    }

    fun paramToSExpression(param: WasmParam): SExpression {
        return S.list(S.symbol("param"), S.identifier(param.identifier), typeToSExpression(param.type))
    }

    fun localToSExpression(param: WasmLocal): SExpression {
        return S.list(S.symbol("local"), S.identifier(param.identifier), typeToSExpression(param.type))
    }

    fun instructionToSExpression(instruction: WasmInstruction): SExpression {
        return when (instruction) {
            is WasmInstruction.Branch -> S.elements(S.symbol("br"), S.identifier(instruction.identifier))
            is WasmInstruction.Call -> S.elements(S.symbol("call"), S.identifier(instruction.identifier))
            is WasmInstruction.Drop -> S.symbol("drop")
            is WasmInstruction.Else -> S.symbol("else")
            is WasmInstruction.End -> S.symbol("end")
            is WasmInstruction.I32Add -> S.symbol("i32.add")
            is WasmInstruction.I32And -> S.symbol("i32.and")
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
            is WasmInstruction.I32Load -> S.symbol("i32.load")
            is WasmInstruction.I32Load8Unsigned -> S.symbol("i32.load8_u")
            is WasmInstruction.I32Multiply -> S.symbol("i32.mul")
            is WasmInstruction.I32NotEqual -> S.symbol("i32.ne")
            is WasmInstruction.I32Store -> S.symbol("i32.store")
            is WasmInstruction.I32Store8 -> S.symbol("i32.store8")
            is WasmInstruction.I32Sub -> S.symbol("i32.sub")
            is WasmInstruction.If -> S.elements(
                S.symbol("if"),
                S.list(S.symbol("result"), typesToSExpressions(instruction.results)),
            )
            is WasmInstruction.LocalSet -> S.elements(S.symbol("local.set"), S.identifier(instruction.identifier))
            is WasmInstruction.Loop -> S.elements(
                S.symbol("loop"),
                S.identifier(instruction.identifier),
                S.list(S.symbol("result"), typesToSExpressions(instruction.results)),
            )
            is WasmInstruction.MemoryGrow -> S.symbol("memory.grow")

            is WasmInstruction.Folded.Call -> S.list(
                S.symbol("call"),
                S.identifier(instruction.identifier),
                S.elements(instruction.args.map(::instructionToSExpression))
            )
            is WasmInstruction.Folded.Drop -> S.list(
                S.symbol("drop"),
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
            is WasmInstruction.Folded.I32Const -> S.list(S.symbol("i32.const"), S.int(instruction.value))
            is WasmInstruction.Folded.I32DivideUnsigned -> S.list(
                S.symbol("i32.div_u"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32Load -> S.list(
                S.symbol("i32.load"),
                instructionToSExpression(instruction.address),
            )
            is WasmInstruction.Folded.I32Multiply -> S.list(
                S.symbol("i32.mul"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
            )
            is WasmInstruction.Folded.I32Store -> S.list(
                S.symbol("i32.store"),
                *(if (instruction.offset == 0) listOf() else listOf(S.symbol("offset=${instruction.offset}"))).toTypedArray(),
                *(if (instruction.alignment == null) listOf() else listOf(S.symbol("align=${instruction.alignment}"))).toTypedArray(),
                instructionToSExpression(instruction.address),
                instructionToSExpression(instruction.value),
            )
            is WasmInstruction.Folded.I32Sub -> S.list(
                S.symbol("i32.sub"),
                instructionToSExpression(instruction.left),
                instructionToSExpression(instruction.right),
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
}

internal object S {
    fun elements(elements: List<SExpression>) = SElements(elements)
    fun elements(vararg elements: SExpression) = SElements(elements.toList())
    val formatBreak = SFormatBreak
    fun int(value: Int) = SInt(value.toBigInteger())
    fun int(value: Long) = SInt(value.toBigInteger())
    fun string(value: String) = SString(value)
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
}

internal data class SElements(private val elements: List<SExpression>): SExpression {
    override fun serialise(): String {
        return elements.joinToString(" ") { element -> element.serialise() }
    }

}
