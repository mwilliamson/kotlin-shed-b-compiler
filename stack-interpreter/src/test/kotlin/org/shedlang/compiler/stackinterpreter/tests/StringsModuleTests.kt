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
    fun codePointCount() {
        val value = call("codePointCount", listOf(InterpreterString("hello")))

        assertThat(value, isInt(5))
    }

    @Test
    fun codePointToHexString() {
        val value = call("codePointToHexString", listOf(InterpreterCodePoint(42)))

        assertThat(value, isString("2A"))
    }

    @Test
    fun codePointToInt() {
        val value = call("codePointToInt", listOf(InterpreterCodePoint(42)))

        assertThat(value, isInt(42))
    }

    @Test
    fun codePointToString() {
        val value = call("codePointToString", listOf(InterpreterCodePoint(42)))

        assertThat(value, isString("*"))
    }

    private fun call(functionName: String, arguments: List<InterpreterValue>): InterpreterValue {
        val instructions = persistentListOf(
            InitModule(moduleName),
            LoadModule(moduleName),
            FieldAccess(Identifier(functionName))
        )
            .addAll(arguments.reversed().map { argument -> PushValue(argument) })
            .add(Call(positionalArgumentCount = 1, namedArgumentNames = listOf()))

        val moduleSet = ModuleSet(listOf(
            Module.Native(name = moduleName, type = moduleType())
        ))
        val image = loadModuleSet(moduleSet)

        return executeInstructions(instructions, image = image)
    }
}
