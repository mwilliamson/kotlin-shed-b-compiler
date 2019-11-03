package org.shedlang.compiler.stackinterpreter

import org.shedlang.compiler.ast.Identifier

internal val ioModule = createNativeModule(
    name = listOf(Identifier("Core"), Identifier("Io")),
    dependencies = listOf(),
    fields = listOf(
        Identifier("print") to InterpreterBuiltinFunction { state, arguments ->
            val value = (arguments[0] as InterpreterString).value
            state.print(value)
            state.pushTemporary(InterpreterUnit)
        }
    )
)
