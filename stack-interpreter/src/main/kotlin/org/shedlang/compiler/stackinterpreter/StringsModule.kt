package org.shedlang.compiler.stackinterpreter

import org.shedlang.compiler.ast.Identifier
import java.lang.Integer.min
import java.math.BigInteger

private val optionsModuleName = listOf(Identifier("Core"), Identifier("Options"))

internal val stringsModule = createNativeModule(
    name = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings")),
    dependencies = listOf(
        optionsModuleName
    ),
    fields = listOf(
        Identifier("codePointCount") to InterpreterBuiltinFunction { state, arguments ->
            val string = (arguments[0] as InterpreterString).value
            state.pushTemporary(InterpreterInt(string.codePointCount(0, string.length).toBigInteger()))
        },

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
        },

        Identifier("next") to InterpreterBuiltinFunction { state, arguments ->
            val index = (arguments[0] as InterpreterInt).value.toInt()
            val string = (arguments[1] as InterpreterString).value
            val optionsModule = state.loadModule(optionsModuleName)
            if (index < string.length) {
                val codePoint = string.codePointAt(index)
                val size = if (codePoint > 0xffff) 2 else 1
                call(
                    state = state,
                    receiver = optionsModule.field(Identifier("some")),
                    positionalArguments = listOf(InterpreterTuple(listOf(
                        InterpreterCodePoint(codePoint),
                        InterpreterInt((index + size).toBigInteger())
                    ))),
                    namedArguments = mapOf()
                )
            } else {
                state.pushTemporary(optionsModule.field(Identifier("none")))
            }
        },

        Identifier("replace") to InterpreterBuiltinFunction { state, arguments ->
            val old = (arguments[0] as InterpreterString).value
            val new = (arguments[1] as InterpreterString).value
            val value = (arguments[2] as InterpreterString).value
            // TODO: handle code points
            state.pushTemporary(InterpreterString(value.replace(old, new)))
        },

        Identifier("substring") to InterpreterBuiltinFunction { state, arguments ->
            val startIndex = (arguments[0] as InterpreterInt).value.intValueExact()
            val endIndex = (arguments[1] as InterpreterInt).value.intValueExact()
            val value = (arguments[2] as InterpreterString).value
            // TODO: handle code points
            val substring = value.substring(startIndex, min(endIndex, value.length))
            state.pushTemporary(InterpreterString(substring))
        },

        Identifier("zeroIndex") to InterpreterInt(BigInteger.ZERO)
    )
)
