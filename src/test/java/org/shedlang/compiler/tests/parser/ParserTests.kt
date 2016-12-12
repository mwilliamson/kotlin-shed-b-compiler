package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionNode
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.SourceLocation
import org.shedlang.compiler.parser.*

class ParserTests {
    @Test
    fun minimalModuleHasModuleDeclaration() {
        val source = """
            module abc;
        """.trimIndent()

        val node = parse("<string>", source)

        assertThat(node, allOf(
            has(ModuleNode::name, equalTo("abc")),
            has(ModuleNode::body, equalTo(listOf()))
        ))
    }

    @Test
    fun moduleCanHaveStatements() {
        val source = """
            module abc;

            fun f() {
            }

            fun g() {
            }
        """.trimIndent()

        val node = parse("<string>", source)

        assertThat(node, allOf(
            has(ModuleNode::body, isSequence(
                has(FunctionNode::name, equalTo("f")),
                has(FunctionNode::name, equalTo("g"))
            ))
        ))
    }

    @Test
    fun moduleNameCanBeOneIdentifier() {
        val source = "abc"
        val name = parseString(::parseModuleName, source)
        assertEquals("abc", name)
    }

    @Test
    fun moduleNameCanBeIdentifiersSeparatedByDots() {
        val source = "abc.de"
        val name = parseString(::parseModuleName, source)
        assertEquals("abc.de", name)
    }

    private fun <T> parseString(parser: (SourceLocation, TokenIterator<TokenType>) -> T, input: String): T {
        val tokens = TokenIterator("<string>", tokenise(input))
        return parser.parse(tokens)
    }

    private fun <T> parseString(parser: (TokenIterator<TokenType>) -> T, input: String): T {
        val tokens = TokenIterator("<string>", tokenise(input))
        return parser(tokens)
    }

    private fun <T> allOf(vararg matchers: Matcher<T>) : Matcher<T> {
        return matchers.reduce { first, second -> first and second }
    }

    private fun <T> isSequence(vararg matchers: Matcher<T>) : Matcher<Iterable<T>> {
        return object : Matcher.Primitive<Iterable<T>>() {
            override fun invoke(actual: Iterable<T>): MatchResult {
                val actualValues = actual.toList()
                val elementResults = actualValues.zip(matchers, {element, matcher -> matcher.invoke(element) })
                val firstMismatch = elementResults.withIndex().firstOrNull { result -> result.value is MatchResult.Mismatch }
                if (firstMismatch != null) {
                    return MatchResult.Mismatch(
                        "item " + firstMismatch.index + ": " + (firstMismatch.value as MatchResult.Mismatch).description
                    )
                } else if (actualValues.size != matchers.size) {
                    return MatchResult.Mismatch("had " + actualValues.size + " elements")
                } else {
                    return MatchResult.Match
                }
            }

            override val description: String
                get() {
                    return "is sequence:\n" + matchers.mapIndexed { index, matcher -> "  " + index + ": " + matcher.description }.joinToString("")
                }

        }
    }
}
