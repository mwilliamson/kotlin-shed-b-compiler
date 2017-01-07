package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.typechecker.UnresolvedReferenceError
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckExpressionStatementTests {
    @Test
    fun expressionIsTypeChecked() {
        val node = ExpressionStatementNode(variableReference("x"), anySourceLocation())
        assertThat(
            { typeCheck(node, emptyTypeContext()) },
            throws(has(UnresolvedReferenceError::name, equalTo("x")))
        )
    }
}
