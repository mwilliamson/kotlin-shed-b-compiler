package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.backends.python.ast.PythonIntegerLiteralNode
import org.shedlang.compiler.backends.python.ast.PythonModuleNode
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.tests.typechecker.anySourceLocation
import org.shedlang.compiler.tests.typechecker.literalInt

class CodeGeneratorTests {
    @Test
    fun emptyModuleGeneratesEmptyModule() {
        val shed = ModuleNode(
            name = "example",
            body = listOf(),
            source = anySourceLocation()
        )

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonModuleNode::statements, equalTo(listOf()))))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonIntegerLiteralNode::value, equalTo(42))))
    }
}
