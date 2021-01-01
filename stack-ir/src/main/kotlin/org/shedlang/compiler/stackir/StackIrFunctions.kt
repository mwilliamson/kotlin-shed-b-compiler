package org.shedlang.compiler.stackir

fun findFreeVariables(instruction: Instruction): List<LocalLoad> {
    val descendants = instruction.descendantsAndSelf()
    val stores = descendants.filterIsInstance<LocalStore>()
    val storeIds = stores.map { store -> store.variableId }
    val parameterIds = descendants.filterIsInstance<DefineFunction>().flatMap { function ->
        val parameters = function.positionalParameters + function.namedParameters
        parameters.map { parameter -> parameter.variableId }
    }
    val localIds = (storeIds + parameterIds).toSet()
    val loads = descendants.filterIsInstance<LocalLoad>().distinctBy { load -> load.variableId }
    return loads.filter { load -> !localIds.contains(load.variableId) }
}
