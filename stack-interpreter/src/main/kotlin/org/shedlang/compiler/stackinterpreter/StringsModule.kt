package org.shedlang.compiler.stackinterpreter

import org.shedlang.compiler.ast.Identifier
import java.math.BigInteger

private val optionsModuleName = listOf(Identifier("Core"), Identifier("Options"))

internal val stringsModule = createNativeModule(
    name = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings")),
    dependencies = listOf(
        optionsModuleName
    ),
    fields = listOf(
        Identifier("unicodeScalarCount") to InterpreterBuiltinFunction { state, arguments ->
            val string = (arguments[0] as InterpreterString).value
            state.pushTemporary(InterpreterInt(string.codePointCount(0, string.length).toBigInteger()))
        },

        Identifier("unicodeScalarToHexString") to InterpreterBuiltinFunction { state, arguments ->
            val unicodeScalar = (arguments[0] as InterpreterUnicodeScalar).value
            state.pushTemporary(InterpreterString(unicodeScalar.toString(16).toUpperCase()))
        },

        Identifier("unicodeScalarToInt") to InterpreterBuiltinFunction { state, arguments ->
            val unicodeScalar = (arguments[0] as InterpreterUnicodeScalar).value
            state.pushTemporary(InterpreterInt(unicodeScalar.toBigInteger()))
        },

        Identifier("unicodeScalarToString") to InterpreterBuiltinFunction { state, arguments ->
            val unicodeScalar = (arguments[0] as InterpreterUnicodeScalar).value
            val builder = StringBuilder()
            builder.appendCodePoint(unicodeScalar)
            state.pushTemporary(InterpreterString(builder.toString()))
        },

        Identifier("dropLeftUnicodeScalars") to InterpreterBuiltinFunction { state, arguments ->
            val count = (arguments[0] as InterpreterInt).value
            val string = (arguments[1] as InterpreterString).value
            val result = string.substring(indexAtUnicodeScalarCount(string, count))
            state.pushTemporary(InterpreterString(result))
        },

        Identifier("next") to InterpreterBuiltinFunction { state, arguments ->
            val stringSlice = arguments[0] as InterpreterStringSlice
            val optionsModule = state.loadModule(optionsModuleName)
            if (stringSlice.startIndex < stringSlice.endIndex) {
                val unicodeScalar = stringSlice.string.codePointAt(stringSlice.startIndex)
                val size = if (unicodeScalar > 0xffff) 2 else 1
                val rest = InterpreterStringSlice(
                    stringSlice.string,
                    stringSlice.startIndex + size,
                    stringSlice.endIndex
                )
                call(
                    state = state,
                    receiver = optionsModule.field(Identifier("some")),
                    positionalArguments = listOf(InterpreterTuple(listOf(
                        InterpreterUnicodeScalar(unicodeScalar),
                        rest
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

        Identifier("slice") to InterpreterBuiltinFunction { state, arguments ->
            val string = (arguments[0] as InterpreterString).value
            state.pushTemporary(InterpreterStringSlice(string, 0, string.length))
        },

        Identifier("substring") to InterpreterBuiltinFunction { state, arguments ->
            val startCount = (arguments[0] as InterpreterInt).value
            val endCount = (arguments[1] as InterpreterInt).value
            val string = (arguments[2] as InterpreterString).value
            // TODO: handle bounds
            val startIndex = indexAtUnicodeScalarCount(string, startCount)
            val endIndex = indexAtUnicodeScalarCount(string, endCount)
            val result = if (startIndex < endIndex) {
                string.substring(startIndex, endIndex)
            } else {
                ""
            }
            state.pushTemporary(InterpreterString(result))
        }
    )
)


fun indexAtUnicodeScalarCount(string: String, countBigInt: BigInteger): Int {
    val count = countBigInt.intValueExact()
    return if (count >= 0) {
        try {
            string.offsetByCodePoints(0, count)
        } catch (error: IndexOutOfBoundsException) {
            string.length
        }
    } else {
        var index = string.length
        var currentCount = 0
        while (currentCount > count && index - 1 >= 0) {
            val codeUnit = string[index - 1]
            val size = if (codeUnit >= 0xdc00.toChar() && codeUnit <= 0xdfff.toChar()) 2 else 1
            index -= size
            currentCount -= 1
        }
        index
    }
}
