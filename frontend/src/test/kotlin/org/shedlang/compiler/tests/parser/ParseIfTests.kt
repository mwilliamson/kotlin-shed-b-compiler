package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.IfStatementNode
import org.shedlang.compiler.parser.parseFunctionStatement
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.isSequence

class ParseIfTests {
    @Test
    fun conditionAndBranchesAreReadForIfStatement() {
        val source = "if (x) { return y; } else { return z; }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, cast(allOf(
            has(IfStatementNode::condition, isVariableReference("x")),
            has(IfStatementNode::trueBranch, isSequence(
                isReturn(isVariableReference("y"))
            )),
            has(IfStatementNode::falseBranch, isSequence(
                isReturn(isVariableReference("z"))
            ))
        )))
    }
}
