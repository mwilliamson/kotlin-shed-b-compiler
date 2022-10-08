package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.parser.InconsistentBranchTerminationError
import org.shedlang.compiler.parser.parseExpression
import org.shedlang.compiler.tests.isSequence

class ParseIfTests {
    @Test
    fun conditionAndBranchesAreReadForIfStatement() {
        val source = "if (x) { y; } else { z; }"
        val node = parseString(::parseExpression, source)
        assertThat(node, isIfNode(
            conditionalBranches = isSequence(
                isConditionalBranchNode(
                    condition = isVariableReferenceNode("x"),
                    body = isBlockNode(
                        isExpressionStatementNode(isVariableReferenceNode("y"))
                    )
                )
            ),
            elseBranch = isBlockNode(
                isExpressionStatementNode(isVariableReferenceNode("z"))
            )
        ))
    }

    @Test
    fun elseBranchIsOptional() {
        val source = "if (x) { y; }"
        val node = parseString(::parseExpression, source)
        assertThat(node, isIfNode(
            conditionalBranches = isSequence(
                isConditionalBranchNode(
                    condition = isVariableReferenceNode("x"),
                    body = isBlockNode(
                        isExpressionStatementNode(isVariableReferenceNode("y"))
                    )
                )
            ),
            elseBranch = isBlockNode()
        ))
    }

    @Test
    fun multipleConditionalBranchesCanBeRead() {
        val source = "if (x) { 0; } else if (y) { 1; }"
        val node = parseString(::parseExpression, source)
        assertThat(node, isIfNode(
            conditionalBranches = isSequence(
                isConditionalBranchNode(
                    condition = isVariableReferenceNode("x"),
                    body = isBlockNode(
                        isExpressionStatementNode(isIntLiteralNode(equalTo(0)))
                    )
                ),

                isConditionalBranchNode(
                    condition = isVariableReferenceNode("y"),
                    body = isBlockNode(
                        isExpressionStatementNode(isIntLiteralNode(equalTo(1)))
                    )
                )
            ),
            elseBranch = isBlockNode()
        ))
    }

    @Test
    fun whenBranchesDoNotAgreeOnTerminationThenErrorIsThrown() {
        val source = "if (x) { y } else { z; };"

        assertThat(
            { parseString(::parseExpression, source) },
            throws<InconsistentBranchTerminationError>()
        )
    }
}
