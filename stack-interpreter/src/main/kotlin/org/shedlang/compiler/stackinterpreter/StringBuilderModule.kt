package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.stackir.Call
import org.shedlang.compiler.stackir.Discard
import org.shedlang.compiler.stackir.Return

private val moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder"))

private val effectId = freshNodeId()

internal val stringBuilderModule = createNativeModule(
    name = moduleName,
    dependencies = listOf(),
    fields = listOf(
        Identifier("build") to InterpreterBuiltinFunction { state, arguments ->
            val func = arguments[0]
            val stringBuilder = StringBuilder()
            val effectHandler = EffectHandler(
                mapOf(
                    Identifier("write") to InterpreterBuiltinOperationHandler { state, arguments, resume ->
                        val value = arguments[0] as InterpreterString
                        stringBuilder.append(value.value)
                        state.copy(callStack = resume).pushTemporary(InterpreterUnit)
                    }
                ),
                stateReference = null,
            )
            state.enter(
                instructions = listOf(
                    Call(positionalArgumentCount = 0, namedArgumentNames = listOf()),
                    Discard,
                    Call(positionalArgumentCount = 0, namedArgumentNames = listOf()),
                    Return
                ),
                parentScopes = persistentListOf(),
                effectHandlers = mapOf(
                    effectId to effectHandler
                )
            ).pushTemporary(InterpreterBuiltinFunction { state, _ ->
                state.pushTemporary(InterpreterString(stringBuilder.toString()))
            }).pushTemporary(func)
        },

        Identifier("write") to InterpreterBuiltinFunction { state, arguments ->
            state.handle(
                effectId = effectId,
                operationName = Identifier("write"),
                positionalArguments = arguments,
                namedArguments = mapOf()
            )
        }
    )
)
