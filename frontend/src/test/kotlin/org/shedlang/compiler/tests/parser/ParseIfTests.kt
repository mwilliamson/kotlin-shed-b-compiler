package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
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

    @Test
    fun multipleConditionalBranchesCanBeRead() {
        val source = "if (x) { return 0; } else if (y) { return 1; }"
        val node = parseString(::parseFunctionStatement, source)
        assertThat(node, isIfStatement(
            conditionalBranches = isSequence(
                isConditionalBranch(
                    condition = isVariableReference("x"),
                    body = isSequence(
                        isReturn(isIntLiteral(equalTo(0)))
                    )
                ),

                isConditionalBranch(
                    condition = isVariableReference("y"),
                    body = isSequence(
                        isReturn(isIntLiteral(equalTo(1)))
                    )
                )
            ),
            elseBranch = isSequence()
        ))
    }
}
