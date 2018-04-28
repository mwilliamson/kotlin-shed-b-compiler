package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.tests.parameter
import org.shedlang.compiler.typechecker.NodeTypesMap
import org.shedlang.compiler.typechecker.UnknownTypeError


class NodeTypesMapTests {
    @Test
    fun whenTypeIsMissingThenExceptionIsThrown() {
        val types = NodeTypesMap(mapOf())
        assertThat(
            { types.typeOf(parameter("x")) },
            throws(has(UnknownTypeError::name, isIdentifier("x")))
        )
    }

}
