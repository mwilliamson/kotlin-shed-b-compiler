package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.UnexpectedTokenException
import org.shedlang.compiler.parser.parseShape
import org.shedlang.compiler.tests.isSequence

class ParseShapeTests {
    @Test
    fun emptyShape() {
        val source = "shape X { }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            name = equalTo("X"),
            typeParameters = isSequence(),
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
                    name = equalTo("a"),
                    type = isStaticReference("Int")
                ),
                isShapeField(
                    name = equalTo("b"),
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
                    name = equalTo("a"),
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
            name = equalTo("X"),
            typeParameters = isSequence(isTypeParameter(name = equalTo("T"))),
            fields = isSequence()
        ))
    }

    @Test
    fun shapeCanHaveManyTypeParameters() {
        val source = "shape X[T, U] { }"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            name = equalTo("X"),
            typeParameters = isSequence(
                isTypeParameter(name = equalTo("T")),
                isTypeParameter(name = equalTo("U"))
            ),
            fields = isSequence()
        ))
    }

    @Test
    fun shapeHasNoTagByDefault() {
        val source = "shape X {}"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            tag = equalTo(false)
        ))
    }

    @Test
    fun shapeTaggedKeywordIsPresentThenShapeHasTag() {
        val source = "shape X tagged {}"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            tag = equalTo(true)
        ))
    }

    @Test
    fun shapeHasNoTagValueByDefault() {
        val source = "shape X {}"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            hasValueForTag = absent()
        ))
    }

    @Test
    fun whenShapeHasTagValueKeywordThenShapeHasTagValue() {
        val source = "shape X tag-value-for Y  {}"
        val node = parseString(::parseShape, source)
        assertThat(node, isShape(
            hasValueForTag = present(isStaticReference("Y"))
        ))
    }
}
