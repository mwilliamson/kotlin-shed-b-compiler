package org.shedlang.compiler.backends.tests

import org.shedlang.compiler.EMPTY_TYPES
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.Types
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.SimpleCodeInspector
import org.shedlang.compiler.stackir.Loader
import org.shedlang.compiler.typechecker.ResolvedReferencesMap

fun loader(
    inspector: CodeInspector = SimpleCodeInspector(),
    references: ResolvedReferences = ResolvedReferencesMap.EMPTY,
    types: Types = EMPTY_TYPES
): Loader {
    return Loader(
        inspector = inspector,
        references = references,
        types = types,
        moduleSet = ModuleSet(modules = listOf())
    )
}
