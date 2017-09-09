package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.testing.isSequence
import org.shedlang.compiler.tests.isBoolType
import org.shedlang.compiler.tests.isIntType
import org.shedlang.compiler.tests.isStringType
import org.shedlang.compiler.tests.isUnionType
import org.shedlang.compiler.types.*

class UnionTests {
    @Test
    fun unionIsLeftWhenRightCanBeCoercedToLeft() {
        val left = SimpleUnionType("T", listOf(IntType, StringType))
        val right = IntType
        val union = union(left, right)
        assertThat(union, cast(equalTo(left)))
    }

    @Test
    fun unionIsRightWhenLeftCanBeCoercedToRight() {
        val left = IntType
        val right = SimpleUnionType("T", listOf(IntType, StringType))
        val union = union(left, right)
        assertThat(union, cast(equalTo(right)))
    }

    @Test
    fun unionIsUnionWhenLeftAndRightCannotBeCoercedToEachOther() {
        val left = IntType
        val right = StringType
        val union = union(left, right)
        assertThat(union, isUnionType(members = isSequence(isIntType, isStringType)))
    }

    @Test
    fun repeatedUnionsFromLeftProduceSingleUnion() {
        val union = union(union(IntType, StringType), BoolType)
        assertThat(union, isUnionType(members = isSequence(isIntType, isStringType, isBoolType)))
    }

    @Test
    fun repeatedUnionsFromRightProduceSingleUnion() {
        val union = union(IntType, union(StringType, BoolType))
        assertThat(union, isUnionType(members = isSequence(isIntType, isStringType, isBoolType)))
    }
}
