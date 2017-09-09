package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.testing.call
import org.shedlang.compiler.testing.expressionStatement
import org.shedlang.compiler.testing.variableReference
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.UnitType

class TypeCheckExpressionStatementTests {
    @Test
    fun expressionIsTypeChecked() {
        val functionReference = variableReference("f")
        val node = expressionStatement(call(functionReference))
        assertThat(
            { typeCheck(node, typeContext(referenceTypes = mapOf(functionReference to UnitType))) },
            throws(has(UnexpectedTypeError::actual, equalTo<Type>(UnitType)))
        )
    }
}
