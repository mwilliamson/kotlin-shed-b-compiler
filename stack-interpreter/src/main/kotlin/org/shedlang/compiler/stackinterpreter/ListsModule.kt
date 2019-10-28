package org.shedlang.compiler.stackinterpreter

import org.shedlang.compiler.ast.Identifier

internal val listsModule = createNativeModule(
    name = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Lists")),
    dependencies = listOf(),
    fields = listOf(
        Identifier("sequenceToList") to InterpreterBuiltinFunction { state, arguments ->
            state
        }
    )
)
