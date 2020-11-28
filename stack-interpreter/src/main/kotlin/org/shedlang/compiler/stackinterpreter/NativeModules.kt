package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName

internal fun loadNativeModules() = nativeModules

internal fun createNativeModule(
    name: ModuleName,
    dependencies: List<ModuleName>,
    fields: List<Pair<Identifier, InterpreterValue>>
): Pair<ModuleName, InterpreterModule> {
    return name to InterpreterModule(fields.toMap())
}

private val nativeModules: PersistentMap<ModuleName, InterpreterModule> = persistentMapOf(
    coreCastModule,
    intToStringModule,
    ioModule,
    processModule,
    stringBuilderModule,
    stringsModule
)
