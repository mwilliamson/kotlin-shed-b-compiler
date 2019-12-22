package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.shedlang.compiler.ast.Identifier

internal fun loadNativeModules() = nativeModules

internal fun createNativeModule(
    name: List<Identifier>,
    dependencies: List<List<Identifier>>,
    fields: List<Pair<Identifier, InterpreterValue>>
): Pair<List<Identifier>, InterpreterModule> {
    return name to InterpreterModule(fields.toMap())
}

private val nativeModules: PersistentMap<List<Identifier>, InterpreterModule> = persistentMapOf(
    intToStringModule,
    ioModule,
    stringsModule
)
