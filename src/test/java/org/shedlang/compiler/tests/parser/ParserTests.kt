package org.shedlang.compiler.tests.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionNode
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.parser.*

class ParserTests {
    @Test
    fun minimalModuleHasModuleDeclaration() {
        val source = """
            module abc;
        """.trimIndent()

        val node = parse(source)

        assertEquals(ModuleNode("abc", listOf()), node)
    }

    @Test
    fun moduleCanHaveStatements() {
        val source = """
            module abc;

            fun f() {
            }
        """.trimIndent()

        val node = parse(source)

        assertEquals(ModuleNode("abc", listOf(FunctionNode(name = "f"))), node)
    }

    @Test
    fun moduleNameCanBeOneIdentifier() {
        val source = "abc"
        val name = parseModuleName(tokens(source))
        assertEquals("abc", name)
    }

    @Test
    fun moduleNameCanBeIdentifiersSeparatedByDots() {
        val source = "abc.de"
        val name = parseModuleName(tokens(source))
        assertEquals("abc.de", name)
    }

    private fun tokens(input: String): TokenIterator<TokenType> {
        return TokenIterator(tokenise(input))
    }
}
