package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.testing.isSequence

class ParseUnionTests {
    @Test
    fun unionHasBarSeparatedMembers() {
        val source = "union X = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            name = equalTo("X"),
            typeParameters = isSequence(),
            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
        ))
    }

    @Test
    fun unionCanHaveTypeParameter() {
        val source = "union X[T] = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            name = equalTo("X"),
            typeParameters = isSequence(isTypeParameter(name = equalTo("T"))),
            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
        ))
    }

    @Test
    fun unionCanHaveManyTypeParameters() {
        val source = "union X[T, U] = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            name = equalTo("X"),
            typeParameters = isSequence(
                isTypeParameter(name = equalTo("T")),
                isTypeParameter(name = equalTo("U"))
            ),
            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
        ))
    }
}
