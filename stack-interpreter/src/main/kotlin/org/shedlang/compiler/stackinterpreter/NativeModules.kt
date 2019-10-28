package org.shedlang.compiler.stackinterpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId

internal fun loadNativeModule(moduleName: List<Identifier>): PersistentList<Instruction> {
    val module = nativeModules[moduleName]
    if (module == null) {
        throw Exception("Could not find native module: $moduleName")
    } else {
        return module
    }
}

internal fun createNativeModule(
    name: List<Identifier>,
    fields: List<Pair<Identifier, InterpreterValue>>
): Pair<List<Identifier>, PersistentList<Instruction>> {
    val fieldIds = fields.map { _ -> freshNodeId() }
    val exports = fields.zip(fieldIds) { (fieldName, _), fieldId ->
        fieldName to fieldId
    }
    val instructions = fields.zip(fieldIds) { (_, fieldValue), fieldId ->
        listOf(
            PushValue(fieldValue),
            StoreLocal(fieldId)
        )
    }.flatten().toPersistentList()
    return name to instructions.add(StoreModule(name, exports = exports)).add(Exit)
}

private val nativeModules: Map<List<Identifier>, PersistentList<Instruction>> = mapOf(
    listsModule,
    stringsModule
)
