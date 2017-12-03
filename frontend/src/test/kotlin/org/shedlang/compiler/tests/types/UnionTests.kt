package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.*

class UnionTests {
    @Test
    fun unionIsLeftWhenRightCanBeCoercedToLeft() {
        val left = unionType("T", listOf(IntType, StringType))
        val right = IntType
        val union = union(left, right)
        assertThat(union, cast(equalTo(left)))
    }

    @Test
    fun unionIsRightWhenLeftCanBeCoercedToRight() {
        val left = IntType
        val right = unionType("T", listOf(IntType, StringType))
        val union = union(left, right)
        assertThat(union, cast(equalTo(right)))
    }

    @Test
    fun unionIsUnionWhenLeftAndRightCannotBeCoercedToEachOther() {
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))

        val union = union(member1, member2)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2)),
            tagField = equalTo(tag)
        ))
    }

    @Test
    fun repeatedUnionsFromLeftProduceSingleUnion() {
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))
        val member3 = shapeType(name = "Member3", tagValue = TagValue(tag, freshNodeId()))

        val union = union(union(member1, member2), member3)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3)),
            tagField = equalTo(tag)
        ))
    }

    @Test
    fun repeatedUnionsFromRightProduceSingleUnion() {
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))
        val member3 = shapeType(name = "Member3", tagValue = TagValue(tag, freshNodeId()))

        val union = union(member1, union(member2, member3))
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2), isType(member3)),
            tagField = equalTo(tag)
        ))
    }
}
