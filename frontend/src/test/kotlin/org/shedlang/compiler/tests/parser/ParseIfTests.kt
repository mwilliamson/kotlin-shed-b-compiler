package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.parseFunctionStatement
import org.shedlang.compiler.tests.isSequence

class ParseIfTests {
    @Test
    fun conditionAndBranchesAreReadForIfStatement() {
        val source = "if (x) { return y; } else { return z; }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isIfStatement(
            conditionalBranches = isSequence(
                isConditionalBranch(
                    condition = isVariableReference("x"),
                    body = isSequence(
                        isReturn(isVariableReference("y"))
                    )
                )
            ),
            elseBranch = isSequence(
                isReturn(isVariableReference("z"))
            )
        ))
    }

    @Test
    fun elseBranchIsOptional() {
        val source = "if (x) { return y; }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isIfStatement(
            conditionalBranches = isSequence(
                isConditionalBranch(
                    condition = isVariableReference("x"),
                    body = isSequence(
                        isReturn(isVariableReference("y"))
                    )
                )
            ),
            elseBranch = isSequence()
        ))
    }
}
