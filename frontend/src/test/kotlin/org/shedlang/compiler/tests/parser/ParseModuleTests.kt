package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.isSequence

class ParseModuleTests {
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

            fun f() -> Unit {
            }

            fun g() -> Unit {
            }
        """.trimIndent()

        val node = parse("<string>", source)

        assertThat(node, allOf(
            has(ModuleNode::body, isSequence(
                isFunction(name = equalTo("f")),
                isFunction(name = equalTo("g"))
            ))
        ))
    }
}
