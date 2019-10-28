package org.shedlang.compiler.stackinterpreter

import org.shedlang.compiler.ast.Identifier

internal val stringsModule = createNativeModule(
    listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings")),
    fields = listOf(
        Identifier("codePointToHexString") to InterpreterBuiltinFunction { state, arguments ->
            val codePoint = (arguments[0] as InterpreterCodePoint).value
            state.pushTemporary(InterpreterString(codePoint.toString(16).toUpperCase()))
        },

        Identifier("codePointToInt") to InterpreterBuiltinFunction { state, arguments ->
            val codePoint = (arguments[0] as InterpreterCodePoint).value
            state.pushTemporary(InterpreterInt(codePoint.toBigInteger()))
        },

        Identifier("codePointToString") to InterpreterBuiltinFunction { state, arguments ->
            val codePoint = (arguments[0] as InterpreterCodePoint).value
            val builder = StringBuilder()
            builder.appendCodePoint(codePoint)
            state.pushTemporary(InterpreterString(builder.toString()))
        }

    )
)
