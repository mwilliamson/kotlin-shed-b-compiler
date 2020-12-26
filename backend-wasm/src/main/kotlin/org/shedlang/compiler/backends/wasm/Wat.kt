package org.shedlang.compiler.backends.wasm

import java.lang.StringBuilder
import java.lang.UnsupportedOperationException

internal object Wat {
    fun module(): SExpression {
        return S.list(
            S.symbol("module"),
            S.formatBreak,
            S.list(S.symbol("memory"), S.list(S.symbol("export"), S.string("memory")), S.int(1)),
        )
    }
}

internal object S {
    val formatBreak = SFormatBreak
    fun int(value: Int) = SInt(value)
    fun string(value: String) = SString(value)
    fun symbol(value: String) = SSymbol(value)
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
