package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ExpressionStatementNode
import org.shedlang.compiler.parser.InconsistentBranchTerminationError
import org.shedlang.compiler.parser.parseExpression
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
        assertThat(node, isWhen(
            expression = isVariableReference("x"),
            branches = isSequence(
                isWhenBranch(
                    type = isStaticReference("Some"),
                    body = isBlock(
                        isExpressionStatement(expression = isIntLiteral(1), type = equalTo(ExpressionStatementNode.Type.VALUE))
                    )
                ),
                isWhenBranch(
                    type = isStaticReference("None"),
                    body = isBlock(
                        isExpressionStatement(expression = isIntLiteral(2), type = equalTo(ExpressionStatementNode.Type.VALUE))
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

        assertThat(node, isWhen(
            branches = isSequence(
                isWhenBranch(
                    type = isStaticReference("Some"),
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

        assertThat(node, isWhen(
            branches = isSequence(
                isWhenBranch(
                    target = isTargetFields(
                        fields = isSequence(
                            isPair(isFieldName("head"), isTargetVariable("value")),
                            isPair(isFieldName("tail"), isTargetVariable("rest")),
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
        assertThat(node, isWhen(
            expression = isVariableReference("x"),
            branches = isSequence(
                isWhenBranch(
                    type = isStaticReference("Some"),
                    body = isBlock(
                        isExpressionStatement(expression = isIntLiteral(1), type = equalTo(ExpressionStatementNode.Type.VALUE))
                    )
                )
            ),
            elseBranch = present(isBlock(
                isExpressionStatement(expression = isIntLiteral(2), type = equalTo(ExpressionStatementNode.Type.VALUE))
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
