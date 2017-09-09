package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.testing.argument
import org.shedlang.compiler.typechecker.NodeTypesMap
import org.shedlang.compiler.typechecker.UnknownTypeError


class NodeTypesMapTests {
    @Test
    fun whenTypeIsMissingThenExceptionIsThrown() {
        val types = NodeTypesMap(mapOf())
        assertThat(
            { types.typeOf(argument("x")) },
            throws(has(UnknownTypeError::name, equalTo("x")))
        )
    }

}
