package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.frontend.parser.InconsistentBranchTerminationError
import org.shedlang.compiler.frontend.parser.parseExpression
import org.shedlang.compiler.tests.isPair
import org.shedlang.compiler.tests.isSequence

class ParseWhenTests {
    @Test
    fun conditionsAndBodiesAreRead() {
        val source = """
            when (x) {
                is Some {
                    1
                }
                is None {
                    2
                }
            }
        """
        val node = parseString(::parseExpression, source)
        assertThat(node, isWhenNode(
            expression = isVariableReferenceNode("x"),
            branches = isSequence(
                isWhenBranchNode(
                    type = isTypeLevelReferenceNode("Some"),
                    body = isBlockNode(
                        isExpressionStatementNode(expression = isIntLiteralNode(1), type = equalTo(ExpressionStatementNode.Type.VALUE))
                    )
                ),
                isWhenBranchNode(
                    type = isTypeLevelReferenceNode("None"),
                    body = isBlockNode(
                        isExpressionStatementNode(expression = isIntLiteralNode(2), type = equalTo(ExpressionStatementNode.Type.VALUE))
                    )
                )
            ),
            elseBranch = absent()
        ))
    }


    @Test
    fun whenBranchConditionHasNoOpeningParensThenTargetIsEmpty() {
        val source = """
            when (x) {
                is Some {
                }
            }
        """

        val node = parseString(::parseExpression, source)

        assertThat(node, isWhenNode(
            branches = isSequence(
                isWhenBranchNode(
                    type = isTypeLevelReferenceNode("Some"),
                ),
            ),
        ))
    }


    @Test
    fun whenBranchConditionHasOpeningParensThenTargetFieldsAreParsed() {
        val source = """
            when (x) {
                is Cons(.head as value, .tail as rest)  {
                }
            }
        """

        val node = parseString(::parseExpression, source)

        assertThat(node, isWhenNode(
            branches = isSequence(
                isWhenBranchNode(
                    target = isTargetFieldsNode(
                        fields = isSequence(
                            isPair(isFieldNameNode("head"), isTargetVariableNode("value")),
                            isPair(isFieldNameNode("tail"), isTargetVariableNode("rest")),
                        ),
                    ),
                ),
            ),
        ))
    }

    @Test
    fun elseBranchIsRead() {
        val source = """
            when (x) {
                is Some {
                    1
                }
                else {
                    2
                }
            }
        """
        val node = parseString(::parseExpression, source)
        assertThat(node, isWhenNode(
            expression = isVariableReferenceNode("x"),
            branches = isSequence(
                isWhenBranchNode(
                    type = isTypeLevelReferenceNode("Some"),
                    body = isBlockNode(
                        isExpressionStatementNode(expression = isIntLiteralNode(1), type = equalTo(ExpressionStatementNode.Type.VALUE))
                    )
                )
            ),
            elseBranch = present(isBlockNode(
                isExpressionStatementNode(expression = isIntLiteralNode(2), type = equalTo(ExpressionStatementNode.Type.VALUE))
            ))
        ))
    }

    @Test
    fun whenBranchesDoNotAgreeOnTerminationThenErrorIsThrown() {
        val source = """
            when (x) {
                is Some {
                    1
                }
                else {
                    2;
                }
            }
        """

        assertThat(
            { parseString(::parseExpression, source) },
            throws<InconsistentBranchTerminationError>()
        )
    }
}
