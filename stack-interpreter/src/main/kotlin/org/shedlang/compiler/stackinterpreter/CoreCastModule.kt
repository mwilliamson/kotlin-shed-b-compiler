package org.shedlang.compiler.stackinterpreter

import org.shedlang.compiler.ast.Identifier

private val optionsModuleName = listOf(Identifier("Core"), Identifier("Options"))

internal val coreCastModule = createNativeModule(
    name = listOf(Identifier("Core"), Identifier("Cast")),
    dependencies = listOf(optionsModuleName),
    fields = listOf(
        Identifier("cast") to InterpreterBuiltinFunction { state, arguments ->
            val optionsModule = state.loadModule(optionsModuleName)

            val type = (arguments[0] as InterpreterShape)
            val value = (arguments[1] as InterpreterShapeValue)
            if (type.tagValue == value.tagValue) {
                call(
                    state = state,
                    receiver = optionsModule.field(Identifier("some")),
                    positionalArguments = listOf(value),
                    namedArguments = mapOf()
                )
            } else {
                state.pushTemporary(optionsModule.field(Identifier("none")))
            }
        }
    )
)
