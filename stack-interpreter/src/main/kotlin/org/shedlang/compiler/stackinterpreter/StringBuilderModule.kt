package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.Call
import org.shedlang.compiler.stackir.Discard
import org.shedlang.compiler.stackir.Return

private val moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder"))

internal val stringBuilderModule = createNativeModule(
    name = moduleName,
    dependencies = listOf(),
    fields = listOf(
        Identifier("build") to InterpreterBuiltinFunction { state, arguments ->
            val func = arguments[0]
            state.pushStringBuilder().enter(
                instructions = listOf(
                    Call(positionalArgumentCount = 0, namedArgumentNames = listOf()),
                    Discard,
                    Call(positionalArgumentCount = 0, namedArgumentNames = listOf()),
                    Return
                ),
                parentScopes = persistentListOf()
            ).pushTemporary(InterpreterBuiltinFunction { state, arguments ->
                val (state2, stringBuilder) = state.popStringBuilder()
                state2.pushTemporary(InterpreterString(stringBuilder.toString()))
            }).pushTemporary(func)
        },

        Identifier("write") to InterpreterBuiltinFunction { state, arguments ->
            val value = arguments[0] as InterpreterString
            state.peekStringBuilder().append(value.value)
            state.nextInstruction()
        }
    )
)

private fun InterpreterState.pushStringBuilder(): InterpreterState {
    return storeNativeContext(moduleName, stringBuilderStack().push(StringBuilder()))
}

private fun InterpreterState.peekStringBuilder(): StringBuilder {
    return stringBuilderStack().last()
}

private fun InterpreterState.popStringBuilder(): Pair<InterpreterState, StringBuilder> {
    val (newStringBuilderStack, stringBuilder) = stringBuilderStack().pop()
    return storeNativeContext(moduleName, newStringBuilderStack) to stringBuilder
}

private fun InterpreterState.stringBuilderStack(): Stack<StringBuilder> {
    val nativeContext = loadNativeContext(moduleName)
    return nativeContext as Stack<StringBuilder>? ?: stackOf()
}
