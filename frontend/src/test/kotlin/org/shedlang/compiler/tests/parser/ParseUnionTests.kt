package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isSequence

class ParseUnionTests {
    @Test
    fun unionHasBarSeparatedMembers() {
        val source = "union X = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            name = equalTo("X"),
            staticParameters = isSequence(),
            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
        ))
    }

    @Test
    fun unionCanHaveTypeParameter() {
        val source = "union X[T] = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            name = equalTo("X"),
            staticParameters = isSequence(isTypeParameter(name = equalTo("T"))),
            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
        ))
    }

    @Test
    fun unionCanHaveManyTypeParameters() {
        val source = "union X[T, U] = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            name = equalTo("X"),
            staticParameters = isSequence(
                isTypeParameter(name = equalTo("T")),
                isTypeParameter(name = equalTo("U"))
            ),
            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
        ))
    }

    @Test
    fun unionHasNoSuperTypeByDefault() {
        val source = "union X = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            superType = equalTo(null)
        ))
    }

    @Test
    fun subTypeSymbolIsUsedToIndicateSuperTypeOfUnion() {
        val source = "union X <: Base = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            superType = present(isStaticReference(name = "Base"))
        ))
    }
}
