package org.shedlang.compiler.backends

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName

object ShedRuntime {
    fun functionSymbolName(moduleName: ModuleName, functionName: Identifier): String {
        return (listOf("shed_module_fun") + moduleName.map(Identifier::value) + listOf(functionName.value)).joinToString("__")
    }

    val stringAdd = "shed_string_add"
    val stringEquals = "shed_string_equals"
    val stringNotEqual = "shed_string_not_equal"
}
