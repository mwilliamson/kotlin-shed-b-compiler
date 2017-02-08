package org.shedlang.compiler.tests.ast

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.StringSource

class SourceTests {
    private data class TestCase(
        val name: String,
        val contents: String,
        val characterIndex: Int,
        val expectedContext: String
    )

    @TestFactory
    fun sourceContextIsSourceLineWithPointer(): List<DynamicTest> {
        return listOf(
            TestCase(
                "one line, first character",
                contents = "abcd",
                characterIndex = 0,
                expectedContext = "abcd\n^"
            ),
            TestCase(
                "one line, last character",
                contents = "abcd",
                characterIndex = 3,
                expectedContext = """
                    abcd
                       ^
                """
            ),
            TestCase(
                "one line, end",
                contents = "abcd",
                characterIndex = 4,
                expectedContext = """
                    abcd
                        ^
                """
            ),
            TestCase(
                "many lines, end",
                contents = """
                    abc
                    def
                """,
                characterIndex = 7,
                expectedContext = """
                    def
                       ^
                """
            ),
            TestCase(
                "first line, first character",
                contents = """
                    abc
                    def
                """,
                characterIndex = 0,
                expectedContext = """
                    abc
                    ^
                """
            ),
            TestCase(
                "first line, last character",
                contents = """
                    abc
                    def
                """,
                characterIndex = 2,
                expectedContext = """
                    abc
                      ^
                """
            ),
            TestCase(
                "last line, first character",
                contents = """
                    abc
                    def
                """,
                characterIndex = 4,
                expectedContext = """
                    def
                    ^
                """
            )
        ).map({ testCase -> DynamicTest.dynamicTest(testCase.name, {
            val source = StringSource(
                filename = "<filename>",
                contents = testCase.contents.trimIndent(),
                characterIndex = testCase.characterIndex
            )

            assertThat(source.context(), equalTo(testCase.expectedContext.trimIndent()))
        })})
    }
}
