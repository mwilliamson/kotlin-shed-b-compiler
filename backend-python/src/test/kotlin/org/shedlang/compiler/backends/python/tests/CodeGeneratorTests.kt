package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.backends.python.ast.PythonBooleanLiteralNode
import org.shedlang.compiler.backends.python.ast.PythonIntegerLiteralNode
import org.shedlang.compiler.backends.python.ast.PythonModuleNode
import org.shedlang.compiler.backends.python.ast.PythonVariableReferenceNode
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.tests.typechecker.anySourceLocation
import org.shedlang.compiler.tests.typechecker.literalBool
import org.shedlang.compiler.tests.typechecker.literalInt
import org.shedlang.compiler.tests.typechecker.variableReference

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
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonBooleanLiteralNode::value, equalTo(true))))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonIntegerLiteralNode::value, equalTo(42))))
    }

    @Test
    fun variableReferenceGenerateVariableReference() {
        val shed = variableReference("x")

        val node = generateCode(shed)

        assertThat(node, cast(has(PythonVariableReferenceNode::name, equalTo("x"))))
    }
}
