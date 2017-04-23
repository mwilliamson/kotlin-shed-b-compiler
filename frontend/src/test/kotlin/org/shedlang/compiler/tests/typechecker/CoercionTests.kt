package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.typechecker.*

class CoercionTests {
    @Test
    fun canCoerceTypeToItself() {
        assertThat(canCoerce(from = UnitType, to = UnitType), equalTo(true))
    }

    @Test
    fun cannotCoerceOneScalarToAnother() {
        assertThat(canCoerce(from = UnitType, to = IntType), equalTo(false))
    }

    @Test
    fun whenTypeIsAMemberOfAUnionThenCanCoerceTypeToUnion() {
        val union = object: UnionType {
            override val name = "X"
            override val members = listOf(UnitType, IntType)
        }

        assertThat(canCoerce(from = UnitType, to = union), equalTo(true))
        assertThat(canCoerce(from = IntType, to = union), equalTo(true))
        assertThat(canCoerce(from = StringType, to = union), equalTo(false))
    }

    @Test
    fun canCoerceUnionToSupersetUnion() {
        val union = object: UnionType {
            override val name = "X"
            override val members = listOf(UnitType, IntType)
        }
        val supersetUnion = object: UnionType {
            override val name = "Y"
            override val members = listOf(UnitType, IntType, StringType)
        }

        assertThat(canCoerce(from = union, to = supersetUnion), equalTo(true))
    }

    @Test
    fun cannotCoerceUnionToSubsetUnion() {
        val union = object: UnionType {
            override val name = "X"
            override val members = listOf(UnitType, IntType, StringType)
        }
        val subsetUnion = object: UnionType {
            override val name = "Y"
            override val members = listOf(UnitType, IntType)
        }

        assertThat(canCoerce(from = union, to = subsetUnion), equalTo(false))
    }
}
