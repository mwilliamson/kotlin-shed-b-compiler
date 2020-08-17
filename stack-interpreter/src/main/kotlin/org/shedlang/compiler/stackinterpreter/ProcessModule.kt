package org.shedlang.compiler.stackinterpreter

import org.shedlang.compiler.ast.Identifier

internal val processModule = createNativeModule(
    name = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Process")),
    dependencies = listOf(),
    fields = listOf(
        Identifier("args") to InterpreterBuiltinFunction { state, arguments ->
            val listsModule = state.loadModule(listOf(Identifier("Stdlib"), Identifier("Lists")))
            var args = listsModule.field(Identifier("nil"))
            val cons = listsModule.field(Identifier("Cons")) as InterpreterShape

            for (arg in state.args().asReversed()) {
                args = InterpreterShapeValue(
                    tagValue = cons.tagValue,
                    fields = mapOf(
                        Identifier("head") to InterpreterString(arg),
                        Identifier("tail") to args
                    )
                )
            }

            state.pushTemporary(args)
        }
    )
)
