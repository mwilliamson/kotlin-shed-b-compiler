package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.tests.moduleType

class StringsModuleTests {
    private val moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings"))

    @Test
    fun codePointToHexString() {
        val instructions = persistentListOf(
            InitModule(moduleName),
            LoadModule(moduleName),
            FieldAccess(Identifier("codePointToHexString")),
            PushValue(InterpreterCodePoint(42)),
            Call(positionalArgumentCount = 1, namedArgumentNames = listOf())
        )
        val moduleSet = ModuleSet(listOf(
            Module.Native(name = moduleName, type = moduleType())
        ))
        val image = loadModuleSet(moduleSet)

        val value = executeInstructions(instructions, image = image)

        assertThat(value, isString("2A"))
    }
}
