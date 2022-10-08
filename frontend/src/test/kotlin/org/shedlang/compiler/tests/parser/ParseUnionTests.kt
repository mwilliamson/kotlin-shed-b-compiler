package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.Variance

class ParseUnionTests {
    @Test
    fun unionHasBarSeparatedMembers() {
        val source = "union X = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            name = isIdentifier("X"),
            typeLevelParameters = isSequence(),
            members = isSequence(
                isUnionMemberNode(name = isIdentifier("Y")),
                isUnionMemberNode(name = isIdentifier("Z"))
            )
        ))
    }

    @Test
    fun whenMemberJustHasIdentifierThenMemberIsEmptyShape() {
        val source = "union X = Y;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            members = isSequence(
                isUnionMemberNode(
                    name = isIdentifier("Y"),
                    typeLevelParameters = isSequence(),
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
        assertThat(node, isUnionNode(
            members = isSequence(
                isUnionMemberNode(
                    fields = isSequence(
                        isShapeFieldNode(name = isIdentifier("z"), type = present(isTypeLevelReferenceNode("Int")))
                    )
                )
            )
        ))
    }

    @Test
    fun unionCanHaveTypeParameter() {
        val source = "union X[T] = Y[T] { y: T };"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            name = isIdentifier("X"),
            typeLevelParameters = isSequence(isTypeParameterNode(name = isIdentifier("T"))),
            members = isSequence(
                isUnionMemberNode(
                    typeLevelParameters = isSequence(isTypeParameterNode(name = isIdentifier("T"))),
                    fields = isSequence(
                        isShapeFieldNode(name = isIdentifier("y"), type = present(isTypeLevelReferenceNode("T")))
                    )
                )
            )
        ))
    }

    @Test
    fun typeParametersOnMembersAreDerivedFromUnion() {
        val source = "union X[+T] = Y[T] { y: T };"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            typeLevelParameters = isSequence(isTypeParameterNode(name = isIdentifier("T"), variance = equalTo(Variance.COVARIANT))),
            members = isSequence(
                isUnionMemberNode(
                    typeLevelParameters = isSequence(isTypeParameterNode(name = isIdentifier("T"), variance = equalTo(Variance.COVARIANT)))
                )
            )
        ))
    }

    @Test
    fun unionCanHaveManyTypeParameters() {
        val source = "union X[T, U] = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            name = isIdentifier("X"),
            typeLevelParameters = isSequence(
                isTypeParameterNode(name = isIdentifier("T")),
                isTypeParameterNode(name = isIdentifier("U"))
            ),
            members = isSequence(
                isUnionMemberNode(name = isIdentifier("Y")),
                isUnionMemberNode(name = isIdentifier("Z"))
            )
        ))
    }

    @Test
    fun typeParametersOnMemberCanBeSubsetOfUnionTypeParameters() {
        val source = "union X[+T1, T2, -T3] = Y[T3] { y: T3 };"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            members = isSequence(
                isUnionMemberNode(
                    typeLevelParameters = isSequence(isTypeParameterNode(name = isIdentifier("T3"), variance = equalTo(Variance.CONTRAVARIANT)))
                )
            )
        ))
    }

    @Test
    fun unionHasNoSuperTypeByDefault() {
        val source = "union X = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            superType = equalTo(null)
        ))
    }

    @Test
    fun subTypeSymbolIsUsedToIndicateSuperTypeOfUnion() {
        val source = "union X <: Base = Y | Z;"
        val node = parseString(::parseModuleStatement, source)
        assertThat(node, isUnionNode(
            superType = present(isTypeLevelReferenceNode(name = "Base"))
        ))
    }
}
