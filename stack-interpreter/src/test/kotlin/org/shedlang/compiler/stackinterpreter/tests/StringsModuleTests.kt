package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.tests.findRoot
import org.shedlang.compiler.readPackage
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

    @Test
    fun firstCodePoint_whenStringIsEmptyThenNoneIsReturned() {
        val value = call("firstCodePoint", listOf(InterpreterString("")))

        assertThat(
            { (value as InterpreterShapeValue).field(Identifier("value")) },
            throws<Exception>()
        )
    }

    @Test
    fun firstCodePoint_whenStringIsNotEmptyThenFirstCodePointIsReturned() {
        val value = call("firstCodePoint", listOf(InterpreterString("hello")))

        val codePoint = (value as InterpreterShapeValue).field(Identifier("value"))
        assertThat(codePoint, isCodePoint('h'))
    }

    private fun call(functionName: String, arguments: List<InterpreterValue>): InterpreterValue {
        val instructions = persistentListOf(
            InitModule(moduleName),
            LoadModule(moduleName),
            FieldAccess(Identifier(functionName))
        )
            .addAll(arguments.reversed().map { argument -> PushValue(argument) })
            .add(Call(positionalArgumentCount = 1, namedArgumentNames = listOf()))

        val optionsModules = readPackage(
            base = findRoot().resolve("stdlib"),
            name = listOf(Identifier("Stdlib"), Identifier("Options"))
        ).modules

        val moduleSet = ModuleSet(optionsModules + listOf(
            Module.Native(name = moduleName, type = moduleType())
        ))
        val image = loadModuleSet(moduleSet)

        return executeInstructions(instructions, image = image)
    }
}
