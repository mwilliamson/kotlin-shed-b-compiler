package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.frontend.tests.isType
import org.shedlang.compiler.frontend.tests.isUnionType
import org.shedlang.compiler.frontend.tests.throwsException
import org.shedlang.compiler.frontend.types.union
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.tests.tagField
import org.shedlang.compiler.tests.unionType
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.types.TagValue

class UnionTests {
    @Test
    fun whenLeftOperandIsSuperTypeOfRightOperandThenResultOfUnionIsLeftOperand() {
        val tagField = tagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, 0))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, 1))
        val superType = unionType("SuperType", listOf(member1, member2), tagField = tagField)

        val union = union(superType, member1)
        assertThat(union, cast(equalTo(superType)))
    }

    @Test
    fun whenRightOperandIsSuperTypeOfLeftOperandThenResultOfUnionIsRightOperand() {
        val tagField = tagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, 0))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, 1))
        val superType = unionType("SuperType", listOf(member1, member2), tagField = tagField)

        val union = union(member1, superType)
        assertThat(union, cast(equalTo(superType)))
    }

    @Test
    fun unioningTwoUnionsWithSameTagFieldReturnsWiderUnion() {
        val tagField = tagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, 0))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, 1))
        val member3 = shapeType(name = "Member3", tagValue = TagValue(tagField, 2))
        val left = unionType("Left", listOf(member1, member2), tagField = tagField)
        val right = unionType("Right", listOf(member2, member3), tagField = tagField)

        val union = union(left, right)
        assertThat(union, isUnionType(
            tagField = equalTo(tagField),
            members = isSequence(isType(member1), isType(member2), isType(member3)
        )))
    }

    @Test
    fun cannotCombineUnionsWithDifferentTagFields() {
        val tagField1 = tagField("Tag1")
        val tagField2 = tagField("Tag2")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField1, 0))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField2, 1))
        val union1 = unionType("Union1", listOf(member1), tagField = tagField1)
        val union2 = unionType("Union2", listOf(member2), tagField = tagField2)

        assertThat(
            { union(union1, union2) },
            // TODO: improve message, include location, more specific subclass
            throwsException(has(TypeCheckError::message, equalTo("Cannot union types with different tag fields")))
        )
    }

    @Test
    fun unioningTwoShapesWithSameTagFieldReturnsUnion() {
        val tag = tagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))

        val union = union(member1, member2)
        assertThat(union, isUnionType(
            members = isSequence(isType(member1), isType(member2)),
            tagField = equalTo(tag)
        ))
    }

    @Test
    fun cannotUnionTwoShapesWithDifferentTagFields() {
        val tagField1 = tagField("Tag1")
        val tagField2 = tagField("Tag2")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField1, 0))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField2, 1))

        assertThat(
            { union(member1, member2) },
            // TODO: improve message, include location, more specific subclass
            throwsException(has(TypeCheckError::message, equalTo("Cannot union types with different tag fields")))
        )

    }

    @Test
    fun repeatedUnionsFromLeftProduceSingleUnion() {
        val tag = tagField("Tag")
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
        val tag = tagField("Tag")
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
