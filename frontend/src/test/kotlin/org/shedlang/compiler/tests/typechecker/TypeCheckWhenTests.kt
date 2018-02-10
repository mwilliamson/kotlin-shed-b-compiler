package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.isUnionTypeGroup
import org.shedlang.compiler.tests.literalInt
import org.shedlang.compiler.tests.whenExpression
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.IntType

class TypeCheckWhenTests {
    @Test
    fun expressionMustBeUnion() {
        val statement = whenExpression(expression = literalInt(1))
        assertThat(
            { typeCheck(statement, typeContext()) },
            throwsUnexpectedType(expected = isUnionTypeGroup, actual = IntType)
        )
    }
}
