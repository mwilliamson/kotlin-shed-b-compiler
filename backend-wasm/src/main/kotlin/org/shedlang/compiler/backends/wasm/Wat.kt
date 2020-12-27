package org.shedlang.compiler.backends.wasm

import java.lang.StringBuilder
import java.lang.UnsupportedOperationException
import java.math.BigInteger

internal object Wat {
    val i32 = S.symbol("i32")
//    val i64 = S.symbol("i64")

//    fun i64Const(value: Long): SList {
//        return S.list(
//            S.symbol("i64.const"),
//            S.int(value),
//        )
//    }

//    fun i64Const(value: Int): SList = i64Const(value.toLong())

    fun module(imports: List<SExpression> = listOf(), memoryPageCount: Int = 0, body: List<SExpression> = listOf()): SExpression {
        return S.list(
            S.symbol("module"),
            S.formatBreak,
            *imports.toTypedArray(),
            S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(memoryPageCount)),
            *body.toTypedArray(),
        )
    }

    fun importFunc(moduleName: String, exportName: String, identifier: String, params: List<SExpression>, result: SExpression): SExpression {
        return S.list(
            S.symbol("import"),
            S.string(moduleName),
            S.string(exportName),
            S.list(
                S.symbol("func"),
                S.identifier(identifier),
                S.list(S.symbol("param"), *params.toTypedArray()),
                S.list(S.symbol("result"), result),
            ),
        )
    }

    fun data(offset: Int, value: String): SExpression {
        return data(offset = Wat.I.i32Const(offset), value = S.string(value))
    }

    fun data(offset: SExpression, value: SExpression): SExpression {
        return S.list(
            S.symbol("data"),
            offset,
            value,
        )
    }

    fun param(identifier: String, type: SExpression): SExpression {
        return S.list(S.symbol("param"), S.identifier(identifier), type)
    }

    fun func(
        identifier: String,
        exportName: String? = null,
        locals: List<SExpression> = listOf(),
        params: List<SExpression> = listOf(),
        result: SExpression? = null,
        body: List<SExpression>,
    ): SExpression {
        val exportExpressions = if (exportName == null) {
            listOf()
        } else {
            listOf(S.list(S.symbol("export"), S.string(exportName)))
        }

        val resultExpressions = if (result == null) {
            listOf()
        } else {
            listOf(S.list(S.symbol("result"), result))
        }

        return S.list(
            S.symbol("func"),
            S.identifier(identifier),
            *exportExpressions.toTypedArray(),
            *params.toTypedArray(),
            *resultExpressions.toTypedArray(),
            S.formatBreak,
            *locals.toTypedArray(),
            *body.toTypedArray(),
        )
    }

    fun local(identifier: String, type: SExpression) = S.list(S.symbol("local"), S.identifier(identifier), type)

    fun start(identifier: String): SExpression {
        return S.list(S.symbol("start"), S.identifier(identifier))
    }

    object I {
        val drop = S.symbol("drop")

        fun i32Const(value: Int): SList {
            return S.list(
                S.symbol("i32.const"),
                S.int(value),
            )
        }

        fun i32Store(offset: SExpression, value: SExpression): SExpression {
            return S.list(S.symbol("i32.store"), offset, value)
        }

        val i32Load = S.list(S.symbol("i32.load"))
        val i32Store = S.list(S.symbol("i32.store"))

        val i32Add = S.list(S.symbol("i32.add"))
        val i32Mul = S.list(S.symbol("i32.mul"))
        val i32Sub = S.list(S.symbol("i32.sub"))

        val i32Eq = S.list(S.symbol("i32.eq"))
        val i32Ne = S.list(S.symbol("i32.ne"))
        val i32GeS = S.list(S.symbol("i32.ge_s"))
        val i32GeU = S.list(S.symbol("i32.ge_u"))
        val i32GtS = S.list(S.symbol("i32.gt_s"))
        val i32GtU = S.list(S.symbol("i32.gt_u"))
        val i32LeS = S.list(S.symbol("i32.le_s"))
        val i32LeU = S.list(S.symbol("i32.le_u"))
        val i32LtS = S.list(S.symbol("i32.lt_s"))
        val i32LtU = S.list(S.symbol("i32.lt_u"))

        fun localGet(identifier: String) = S.list(S.symbol("local.get"), S.identifier(identifier))
        fun localSet(identifier: String) = S.list(S.symbol("local.set"), S.identifier(identifier))

        fun if_(result: List<SExpression>): List<SExpression> {
            return listOf(
                S.symbol("if"),
                S.list(S.symbol("result"), *result.toTypedArray()),
            )
        }

        val else_ = S.symbol("else")
        val end = S.symbol("end")

        fun call(identifier: String, args: List<SExpression>): SExpression {
            return S.list(S.symbol("call"), S.identifier(identifier), *args.toTypedArray())
        }
    }
}

internal object S {
    val formatBreak = SFormatBreak
    fun int(value: Int) = SInt(value.toBigInteger())
    fun int(value: Long) = SInt(value.toBigInteger())
    fun string(value: String) = SString(value)
    fun symbol(value: String) = SSymbol(value)
    fun identifier(value: String) = SIdentifier(value)
    fun list(elements: List<SExpression>) = SList(elements)
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
