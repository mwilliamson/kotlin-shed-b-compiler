package org.shedlang.compiler.backends.wasm

import java.lang.StringBuilder
import java.lang.UnsupportedOperationException

internal object Wat {
    val i32 = S.symbol("i32")

    fun i32Const(value: Int): SList {
        return S.list(
            S.symbol("i32.const"),
            S.int(value),
        )
    }

    fun module(imports: List<SExpression> = listOf()): SExpression {
        return S.list(
            S.symbol("module"),
            S.formatBreak,
            *imports.toTypedArray(),
            S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(1)),
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

    fun data(offset: SExpression, value: SExpression): SExpression {
        return S.list(
            S.symbol("data"),
            offset,
            value,
        )
    }

    fun func(identifier: String, body: List<SExpression>): SExpression {
        return S.list(
            S.symbol("func"),
            S.identifier(identifier),
            S.formatBreak,
            *body.toTypedArray(),
        )
    }

    object I {
        val drop = S.symbol("drop")
    }
}

internal object S {
    val formatBreak = SFormatBreak
    fun int(value: Int) = SInt(value)
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

internal data class SInt(val value: Int): SExpression {
    override fun serialise(): String {
        return value.toString()
    }
}

internal data class SString(val value: String): SExpression {
    override fun serialise(): String {
        // TODO: handle escaping
        return "\"${value}\""
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
                builder.append(element.serialise())
            }
        }

        builder.append(end)
        builder.append(")")
        return builder.toString()
    }
}
