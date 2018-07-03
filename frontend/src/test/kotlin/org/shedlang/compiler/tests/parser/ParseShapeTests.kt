package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.parser.parseShape
import org.shedlang.compiler.tests.isSequence

class ParseShapeTests {
    @Test
    fun emptyShape() {
        val source = "shape X { }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            name = isIdentifier("X"),
            staticParameters = isSequence(),
            fields = isSequence()
        ))
    }

    @Test
    fun emptyShapeMayNotHaveTrailingComma() {
        val source = "shape X { , }"
        assertThat(
            { parseString(::parseShape, source) },
            throws<UnexpectedTokenException>()
        )
    }

    @Test
    fun shapeHasCommaSeparatedFields() {
        val source = "shape X { a: Int, b: String }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            fields = isSequence(
                isShapeField(
                    name = isIdentifier("a"),
                    type = isStaticReference("Int")
                ),
                isShapeField(
                    name = isIdentifier("b"),
                    type = isStaticReference("String")
                )
            )
        ))
    }

    @Test
    fun fieldMayHaveTrailingComma() {
        val source = "shape X { a: Int, }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            fields = isSequence(
                isShapeField(
                    name = isIdentifier("a"),
                    type = isStaticReference("Int")
                )
            )
        ))
    }

    @Test
    fun shapeCanHaveTypeParameter() {
        val source = "shape X[T] { }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            name = isIdentifier("X"),
            staticParameters = isSequence(isTypeParameter(name = isIdentifier("T"))),
            fields = isSequence()
        ))
    }

    @Test
    fun shapeCanHaveManyTypeParameters() {
        val source = "shape X[T, U] { }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            name = isIdentifier("X"),
            staticParameters = isSequence(
                isTypeParameter(name = isIdentifier("T")),
                isTypeParameter(name = isIdentifier("U"))
            ),
            fields = isSequence()
        ))
    }
}
