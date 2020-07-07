package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.throwsException
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.typechecker.SourceError

class ParseIfTests {
    @Test
    fun conditionAndBranchesAreReadForIfStatement() {
        val source = "if (x) { y; } else { z; };"
        val node = parseString(::parseExpression, source)
        assertThat(node, isIf(
            conditionalBranches = isSequence(
                isConditionalBranch(
                    condition = isVariableReference("x"),
                    body = isBlock(
                        isExpressionStatement(isVariableReference("y"))
                    )
                )
            ),
            elseBranch = isBlock(
                isExpressionStatement(isVariableReference("z"))
            )
        ))
    }

    @Test
    fun elseBranchIsOptional() {
        val source = "if (x) { y; }"
        val node = parseString(::parseExpression, source)
        assertThat(node, isIf(
            conditionalBranches = isSequence(
                isConditionalBranch(
                    condition = isVariableReference("x"),
                    body = isBlock(
                        isExpressionStatement(isVariableReference("y"))
                    )
                )
            ),
            elseBranch = isBlock()
        ))
    }

    @Test
    fun multipleConditionalBranchesCanBeRead() {
        val source = "if (x) { 0; } else if (y) { 1; };"
        val node = parseString(::parseExpression, source)
        assertThat(node, isIf(
            conditionalBranches = isSequence(
                isConditionalBranch(
                    condition = isVariableReference("x"),
                    body = isBlock(
                        isExpressionStatement(isIntLiteral(equalTo(0)))
                    )
                ),

                isConditionalBranch(
                    condition = isVariableReference("y"),
                    body = isBlock(
                        isExpressionStatement(isIntLiteral(equalTo(1)))
                    )
                )
            ),
            elseBranch = isBlock()
        ))
    }

    @Test
    fun whenBranchesDoNotAgreeOnTerminationThenErrorIsThrown() {
        val source = "if (x) { y } else { z; };"

        assertThat(
            { parseString(::parseExpression, source) },
            throwsException(has(SourceError::message, equalTo("Some branches do not provide a value")))
        )
    }
}
