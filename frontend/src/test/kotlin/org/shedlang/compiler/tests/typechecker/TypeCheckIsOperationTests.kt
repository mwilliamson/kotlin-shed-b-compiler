package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.testing.isOperation
import org.shedlang.compiler.testing.literalBool
import org.shedlang.compiler.testing.staticReference
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.MetaType

class TypeCheckIsOperationTests {
    @Test
    fun isOperationHasBooleanType() {
        val booleanType = staticReference("Boolean")
        val node = isOperation(
            expression = literalBool(),
            type = booleanType
        )

        val typeContext = typeContext(referenceTypes = mapOf(booleanType to MetaType(BoolType)))
        val type = inferType(node, typeContext)
        assertThat(type, cast(equalTo(BoolType)))
    }
}
