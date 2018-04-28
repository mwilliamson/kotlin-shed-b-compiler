package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.TypesModuleNode
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.parser.parseTypesModule
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.isSequence

class ParseTypesModuleTests {
    @Test
    fun minimalTypesModuleIsEmptyString() {
        val source = "".trimIndent()

        val node = parseTypesModule("<string>", source)

        assertThat(node, allOf(
            has(TypesModuleNode::imports, isSequence()),
            has(TypesModuleNode::body, isSequence())
        ))
    }

    @Test
    fun typesModuleCanHaveImports() {
        val source = """
            import .x.y;
        """.trimIndent()

        val node = parseTypesModule("<string>", source)

        assertThat(node, has(TypesModuleNode::imports, isSequence(
            isImport(equalTo(ImportPath.relative(listOf("x", "y"))))
        )))
    }

    @Test
    fun typesModuleCanDeclareValueTypes() {
        val source = """
            val x: Int;
        """.trimIndent()

        val node = parseTypesModule("<string>", source)

        assertThat(node, has(TypesModuleNode::body, isSequence(
            isValType(name = isIdentifier("x"), type = isStaticReference("Int"))
        )))
    }
}
