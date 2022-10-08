package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.UnexpectedTokenError
import org.shedlang.compiler.parser.parseFunctionStatement
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence

class ParseShapeTests {
    @Test
    fun canParseShapeAsModuleStatement() {
        val source = "shape X { }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            name = isIdentifier("X"),
        ))
    }

    @Test
    fun canParseShapeAsFunctionStatement() {
        val source = "shape X { }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isShapeNode(
            name = isIdentifier("X"),
        ))
    }

    @Test
    fun emptyShape() {
        val source = "shape X { }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            name = isIdentifier("X"),
            typeLevelParameters = isSequence(),
            extends = isSequence(),
            fields = isSequence()
        ))
    }

    @Test
    fun emptyShapeMayNotHaveTrailingComma() {
        val source = "shape X { , }"
        assertThat(
            { parseString(::parseModuleStatement, source) },
            throws<UnexpectedTokenError>()
        )
    }

    @Test
    fun shapeHasCommaSeparatedFields() {
        val source = "shape X { a: Int, b: String }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            fields = isSequence(
                isShapeFieldNode(
                    name = isIdentifier("a"),
                    type = present(isTypeLevelReferenceNode("Int")),
                ),
                isShapeFieldNode(
                    name = isIdentifier("b"),
                    type = present(isTypeLevelReferenceNode("String")),
                )
            )
        ))
    }

    @Test
    fun fieldMayHaveTrailingComma() {
        val source = "shape X { a: Int, }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            fields = isSequence(
                isShapeFieldNode(
                    name = isIdentifier("a"),
                    type = present(isTypeLevelReferenceNode("Int")),
                )
            )
        ))
    }

    @Test
    fun fieldShapeIsNullIfNotExplicitlySet() {
        val source = "shape X { a: Int, }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            fields = isSequence(
                isShapeFieldNode(
                    name = isIdentifier("a"),
                    shape = absent()
                )
            )
        ))
    }

    @Test
    fun fieldShapeIsSetUsingFromKeyword() {
        val source = "shape X { a from Y: Int, }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            fields = isSequence(
                isShapeFieldNode(
                    name = isIdentifier("a"),
                    shape = present(isTypeLevelReferenceNode("Y"))
                )
            )
        ))
    }

    @Test
    fun shapeCanExtendSingleShape() {
        val source = "shape X extends Y { }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            extends = isSequence(
                isTypeLevelReferenceNode("Y")
            )
        ))
    }

    @Test
    fun shapeCanExtendMultipleShape() {
        val source = "shape X extends Y, Z { }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            extends = isSequence(
                isTypeLevelReferenceNode("Y"),
                isTypeLevelReferenceNode("Z")
            )
        ))
    }

    @Test
    fun shapeCanHaveTypeParameter() {
        val source = "shape X[T] { }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            name = isIdentifier("X"),
            typeLevelParameters = isSequence(isTypeParameterNode(name = isIdentifier("T"))),
            fields = isSequence()
        ))
    }

    @Test
    fun shapeCanHaveManyTypeParameters() {
        val source = "shape X[T, U] { }"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isShapeNode(
            name = isIdentifier("X"),
            typeLevelParameters = isSequence(
                isTypeParameterNode(name = isIdentifier("T")),
                isTypeParameterNode(name = isIdentifier("U"))
            ),
            fields = isSequence()
        ))
    }
}
