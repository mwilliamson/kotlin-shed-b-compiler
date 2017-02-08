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
                contents = """abcd""",
                characterIndex = 0,
                expectedContext = """
                    <filename>:1:1
                    abcd
                    ^
                """
            ),
            TestCase(
                "one line, last character",
                contents = "abcd",
                characterIndex = 3,
                expectedContext = """
                    <filename>:1:4
                    abcd
                       ^
                """
            ),
            TestCase(
                "one line, end",
                contents = "abcd",
                characterIndex = 4,
                expectedContext = """
                    <filename>:1:5
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
                    <filename>:2:4
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
                    <filename>:1:1
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
                    <filename>:1:3
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
                    <filename>:2:1
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

            assertThat(source.describe(), equalTo(testCase.expectedContext.trimIndent()))
        })})
    }
}
