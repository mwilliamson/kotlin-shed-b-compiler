package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.types.AnonymousUnionType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.StringType

class AnonymousUnionTests {
    @Test
    fun anonymousUnionShortDescriptionContainsAllMembers() {
        val type = AnonymousUnionType(members = listOf(IntType, StringType))

        assertThat(type.shortDescription, equalTo("Int | String"))
    }
}
