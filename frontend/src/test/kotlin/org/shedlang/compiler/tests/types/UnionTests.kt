package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.isType
import org.shedlang.compiler.frontend.tests.isUnionType
import org.shedlang.compiler.frontend.types.union
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.tests.unionType

class UnionTests {
    @Test
    fun whenLeftOperandIsSuperTypeOfRightOperandThenResultOfUnionIsLeftOperand() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val superType = unionType("SuperType", listOf(member1, member2))

        val union = union(superType, member1)
        assertThat(union, cast(equalTo(superType)))
    }

    @Test
    fun whenRightOperandIsSuperTypeOfLeftOperandThenResultOfUnionIsRightOperand() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val superType = unionType("SuperType", listOf(member1, member2))

        val union = union(member1, superType)
        assertThat(union, cast(equalTo(superType)))
    }

    @Test
    fun unioningTwoUnionsReturnsWiderUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val member3 = shapeType(name = "Member3")
        val left = unionType("Left", listOf(member1, member2))
        val right = unionType("Right", listOf(member2, member3))

        val union = union(left, right)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3)
        )))
    }

    @Test
    fun unioningTwoShapesReturnsUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")

        val union = union(member1, member2)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2))
        ))
    }

    @Test
    fun repeatedUnionsFromLeftProduceSingleUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val member3 = shapeType(name = "Member3")

        val union = union(union(member1, member2), member3)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3))
        ))
    }

    @Test
    fun repeatedUnionsFromRightProduceSingleUnion() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
        val member3 = shapeType(name = "Member3")

        val union = union(member1, union(member2, member3))
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3))
        ))
    }
}
