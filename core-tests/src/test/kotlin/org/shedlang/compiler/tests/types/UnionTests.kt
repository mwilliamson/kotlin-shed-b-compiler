package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.union

class UnionTests {
    @Test
    fun whenLeftOperandIsSuperTypeOfRightOperandThenResultOfUnionIsLeftOperand() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val superType = unionType("SuperType", tag, listOf(member1, member2))

        val union = union(superType, member1)
        assertThat(union, cast(equalTo(superType)))
    }

    @Test
    fun whenRightOperandIsSuperTypeOfLeftOperandThenResultOfUnionIsRightOperand() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val superType = unionType("SuperType", tag, listOf(member1, member2))

        val union = union(member1, superType)
        assertThat(union, cast(equalTo(superType)))
    }

    @Test
    fun unioningTwoUnionsReturnsWiderUnion() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val member3 = shapeType(name = "Member3", tagValue = tagValue(tag, "Member3"))
        val left = unionType("Left", tag, listOf(member1, member2))
        val right = unionType("Right", tag, listOf(member2, member3))

        val union = union(left, right)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3)
        )))
    }

    @Test
    fun unioningTwoShapesWithSameTagReturnsUnion() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))

        val union = union(member1, member2)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2))
        ))
    }

    @Test
    fun repeatedUnionsFromLeftProduceSingleUnion() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val member3 = shapeType(name = "Member3", tagValue = tagValue(tag, "Member3"))

        val union = union(union(member1, member2), member3)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3))
        ))
    }

    @Test
    fun repeatedUnionsFromRightProduceSingleUnion() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val member3 = shapeType(name = "Member3", tagValue = tagValue(tag, "Member3"))

        val union = union(member1, union(member2, member3))
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3))
        ))
    }
}
