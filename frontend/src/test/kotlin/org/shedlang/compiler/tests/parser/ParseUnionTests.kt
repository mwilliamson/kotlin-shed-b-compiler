package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isSequence

class ParseUnionTests {
    @Test
    fun unionHasBarSeparatedMembers() {
        val source = "union X = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            name = isIdentifier("X"),
            staticParameters = isSequence(),
            members = isSequence(
                isUnionMember(name = isIdentifier("Y")),
                isUnionMember(name = isIdentifier("Z"))
            )
        ))
    }

    @Test
    fun whenMemberJustHasIdentifierThenMemberIsEmptyShape() {
        val source = "union X = Y;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            members = isSequence(
                isUnionMember(
                    name = isIdentifier("Y"),
                    staticParameters = isSequence(),
                    extends = isSequence(),
                    fields = isSequence()
                )
            )
        ))
    }

    @Test
    fun memberCanHaveFields() {
        val source = "union X = Y { z: Int, };"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnion(
            members = isSequence(
                isUnionMember(
                    fields = isSequence(
                        isShapeField(name = isIdentifier("z"), type = present(isStaticReference("Int")))
                    )
                )
            )
        ))
    }

    @Test
    @Disabled("WIP")
    fun unionCanHaveTypeParameter() {
//        val source = "union X[T] = Y | Z;"
//        val node = parseString(::parseModuleStatement, source)
//        assertThat(node, isUnion(
//            name = isIdentifier("X"),
//            staticParameters = isSequence(isTypeParameter(name = isIdentifier("T"))),
//            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
//        ))
    }

    @Test
    @Disabled("WIP")
    fun unionCanHaveManyTypeParameters() {
//        val source = "union X[T, U] = Y | Z;"
//        val node = parseString(::parseModuleStatement, source)
//        assertThat(node, isUnion(
//            name = isIdentifier("X"),
//            staticParameters = isSequence(
//                isTypeParameter(name = isIdentifier("T")),
//                isTypeParameter(name = isIdentifier("U"))
//            ),
//            members = isSequence(isStaticReference("Y"), isStaticReference("Z"))
//        ))
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
