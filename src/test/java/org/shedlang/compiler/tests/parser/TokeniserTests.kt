package org.shedlang.compiler.tests.parser

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.parser.Token
import org.shedlang.compiler.parser.TokenType
import org.shedlang.compiler.parser.tokenise
import kotlin.test.assertEquals

class TokeniserTests {
    @TestFactory
    fun keywordsAreTokenised(): List<DynamicTest> {
        return listOf("if", "else", "module").map { keyword ->
            DynamicTest.dynamicTest(keyword, {
                assertEquals(
                    listOf(Token(0, TokenType.KEYWORD, keyword)),
                    tokenise(keyword)
                )
            })
        }
    }

    @TestFactory
    fun identifiersAreTokenised(): List<DynamicTest> {
        return listOf("x", "one", "x1", "ONE").map { identifier ->
            DynamicTest.dynamicTest(identifier, {
                assertEquals(
                    listOf(Token(0, TokenType.IDENTIFIER, identifier)),
                    tokenise(identifier)
                )
            })
        }
    }

    @TestFactory
    fun symbolsAreTokenised(): List<DynamicTest> {
        return listOf(".", ",", ":").map { symbol ->
            DynamicTest.dynamicTest(symbol, {
                assertEquals(
                    listOf(Token(0, TokenType.SYMBOL, symbol)),
                    tokenise(symbol)
                )
            })
        }
    }

    data class WhitespaceTestCase(val input: String, val description: String)

    @TestFactory
    fun whitespaceIsTokenised(): List<DynamicTest> {
        return listOf(
            WhitespaceTestCase("\n", "line feed"),
            WhitespaceTestCase("\r", "carriage return"),
            WhitespaceTestCase("\t", "tab"),
            WhitespaceTestCase(" ", "space")
        ).map { case ->
            DynamicTest.dynamicTest(case.description, {
                assertEquals(
                    listOf(Token(0, TokenType.WHITESPACE, case.input)),
                    tokenise(case.input)
                )
            })
        }
    }
}
