package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.CannotUnionTypesError
import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.IntType
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

    @Test
    fun givenLeftIsNeitherUnionNorShapeWhenUnioningDistinctTypesThenErrorIsThrown() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))

        val func = { union(IntType, member1, source = TestSource) }

        assertThat(func, throwsException(allOf(
            has(CannotUnionTypesError::left, isIntType),
            has(CannotUnionTypesError::right, isType(member1)),
            has(CannotUnionTypesError::source, equalTo(TestSource)),
        )))
    }

    @Test
    fun givenRightIsNeitherUnionNorShapeWhenUnioningDistinctTypesThenErrorIsThrown() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))

        val func = { union(member1, IntType, source = TestSource) }

        assertThat(func, throwsException(allOf(
            has(CannotUnionTypesError::left, isType(member1)),
            has(CannotUnionTypesError::right, isIntType),
            has(CannotUnionTypesError::source, equalTo(TestSource)),
        )))
    }

    @Test
    fun cannotUnionShapesWithoutTagValue() {
        val tag = tag(listOf("Example"), "Tag")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = null)

        val func = { union(member1, member2, source = TestSource) }

        assertThat(func, throwsException(allOf(
            has(CannotUnionTypesError::left, isType(member1)),
            has(CannotUnionTypesError::right, isType(member2)),
            has(CannotUnionTypesError::source, equalTo(TestSource)),
        )))
    }

    @Test
    fun cannotUnionShapesWithDistinctTags() {
        val tag1 = tag(listOf("Example"), "Tag1")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag1, "Member1"))
        val tag2 = tag(listOf("Example"), "Tag2")
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag2, "Member2"))

        val func = { union(member1, member2, source = TestSource) }

        assertThat(func, throwsException(allOf(
            has(CannotUnionTypesError::left, isType(member1)),
            has(CannotUnionTypesError::right, isType(member2)),
            has(CannotUnionTypesError::source, equalTo(TestSource)),
        )))
    }

    object TestSource: Source {
        override fun describe(): String {
            return "(test source)"
        }
    }
}
