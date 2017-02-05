package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.CodeGenerationContext
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.tests.typechecker.VariableReferencesMap

class CodeGeneratorTests {
    @Test
    fun emptyModuleGeneratesEmptyModule() {
        val shed = module(body = listOf())

        val node = generateCode(shed)

        assertThat(node, isPythonModule(equalTo(listOf())))
    }

    @Test
    fun moduleGeneratesModule() {
        val shed = module(body = listOf(function(name = "f")))

        val node = generateCode(shed)

        assertThat(node, isPythonModule(
            body = isSequence(isPythonFunction(name = equalTo("f")))
        ))
    }

    @Test
    fun functionGeneratesFunction() {
        val shed = function(
            name = "f",
            arguments = listOf(argument("x"), argument("y")),
            body = listOf(returns(literalInt(42)))
        )

        val node = generateCode(shed)

        assertThat(node, isPythonFunction(
            name = equalTo("f"),
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isSequence(isPythonReturn(isPythonIntegerLiteral(42)))
        ))
    }

    @Test
    fun expressionStatementGeneratesExpressionStatement() {
        val shed = expressionStatement(literalInt(42))

        val node = generateCode(shed)

        assertThat(node, cast(has(
            PythonExpressionStatementNode::expression,
            isPythonIntegerLiteral(42)
        )))
    }

    @Test
    fun returnStatementGeneratesReturnStatement() {
        val shed = returns(literalInt(42))

        val node = generateCode(shed)

        assertThat(node, cast(has(
            PythonReturnNode::expression,
            isPythonIntegerLiteral(42)
        )))
    }

    @Test
    fun ifStatementGeneratesIfStatement() {
        val shed = ifStatement(
            literalInt(42),
            listOf(returns(literalInt(0))),
            listOf(returns(literalInt(1)))
        )

        val node = generateCode(shed)

        assertThat(node, cast(allOf(
            has(PythonIfStatementNode::condition, isPythonIntegerLiteral(42)),
            has(PythonIfStatementNode::trueBranch, isSequence(
                isPythonReturn(isPythonIntegerLiteral(0))
            )),
            has(PythonIfStatementNode::falseBranch, isSequence(
                isPythonReturn(isPythonIntegerLiteral(1))
            ))
        )))
    }

    @Test
    fun whenSeparateScopesHaveSameNameInSamePythonScopeThenVariablesAreRenamed() {
        val outerVal = valStatement(name = "x")
        val trueVal = valStatement(name = "x")
        val falseVal = valStatement(name = "x")

        val outerReference = variableReference("x")
        val trueReference = variableReference("x")
        val falseReference = variableReference("x")

        val references: Map<ReferenceNode, VariableBindingNode> = mapOf(
            outerReference to outerVal,
            trueReference to trueVal,
            falseReference to falseVal
        )

        val shed = listOf(
            outerVal,
            ifStatement(
                literalBool(),
                listOf(
                    trueVal,
                    returns(outerReference),
                    returns(trueReference)
                ),
                listOf(
                    falseVal,
                    returns(falseReference)
                )
            )
        )

        val nodes = generateCode(shed, context(references))

        assertThat(nodes, isSequence(
            isPythonAssignment(name = equalTo("x")),
            cast(allOf(
                has(PythonIfStatementNode::trueBranch, isSequence(
                    isPythonAssignment(name = equalTo("x_1")),
                    isPythonReturn(isPythonVariableReference("x")),
                    isPythonReturn(isPythonVariableReference("x_1"))
                )),
                has(PythonIfStatementNode::falseBranch, isSequence(
                    isPythonAssignment(name = equalTo("x_2")),
                    isPythonReturn(isPythonVariableReference("x_2"))
                ))
            ))
        ))
    }

    @Test
    fun valGeneratesAssignment() {
        val shed = valStatement(name = "x", expression = literalInt(42))

        val node = generateCode(shed)

        assertThat(node, isPythonAssignment(equalTo("x"), isPythonIntegerLiteral(42)))
    }

    @Test
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, isPythonBooleanLiteral(true))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, isPythonIntegerLiteral(42))
    }

    @Test
    fun stringLiteralGeneratesStringLiteral() {
        val shed = literalString("<string>")
        val node = generateCode(shed)
        assertThat(node, isPythonStringLiteral("<string>"))
    }

    @Test
    fun variableReferenceGeneratesVariableReference() {
        val declaration = argument("x")
        val shed = variableReference("x")

        val node = generateCode(shed, context(mapOf(shed to declaration)))

        assertThat(node, isPythonVariableReference("x"))
    }

    @TestFactory
    fun binaryOperationGeneratesBinaryOperation(): List<DynamicTest> {
        return listOf(
            Operator.ADD to PythonOperator.ADD,
            Operator.SUBTRACT to PythonOperator.SUBTRACT,
            Operator.MULTIPLY to PythonOperator.MULTIPLY,
            Operator.EQUALS to PythonOperator.EQUALS
        ).map({ operator ->  DynamicTest.dynamicTest(
            operator.first.toString(), {
                val shed = binaryOperation(
                    operator = operator.first,
                    left = literalInt(0),
                    right = literalInt(1)
                )

                val node = generateCode(shed)

                assertThat(node, isPythonBinaryOperation(
                    operator = equalTo(operator.second),
                    left = isPythonIntegerLiteral(0),
                    right = isPythonIntegerLiteral(1)
                ))
            })
        })
    }

    @Test
    fun functionCallGeneratesFunctionCall() {
        val declaration = argument("f")
        val function = variableReference("f")
        val shed = functionCall(function, listOf(literalInt(42)))

        val node = generateCode(shed, context(mapOf(function to declaration)))

        assertThat(node, isPythonFunctionCall(
            isPythonVariableReference("f"),
            isSequence(isPythonIntegerLiteral(42))
        ))
    }

    private fun generateCode(node: ModuleNode) = generateCode(node, context())
    private fun generateCode(node: FunctionNode) = generateCode(node, context())
    private fun generateCode(node: List<StatementNode>) = generateCode(node, context())
    private fun generateCode(node: StatementNode) = generateCode(node, context())
    private fun generateCode(node: ExpressionNode) = generateCode(node, context())

    private fun context(
        references: Map<ReferenceNode, VariableBindingNode> = mapOf()
    ) = CodeGenerationContext(
        references = VariableReferencesMap(references.entries.associate({ entry -> entry.key.nodeId to entry.value.nodeId}))
    )

    private fun isPythonModule(body: Matcher<List<PythonStatementNode>>)
        = cast(has(PythonModuleNode::body, body))

    private fun isPythonFunction(
        name: Matcher<String>,
        arguments: Matcher<List<String>> = anything,
        body: Matcher<List<PythonStatementNode>> = anything
    ) : Matcher<PythonStatementNode>
        = cast(allOf(
            has(PythonFunctionNode::name, name),
            has(PythonFunctionNode::arguments, arguments),
            has(PythonFunctionNode::body, body)
        ))

    private fun isPythonReturn(expression: Matcher<PythonExpressionNode>)
        : Matcher<PythonStatementNode>
        = cast(has(PythonReturnNode::expression, expression))

    private fun isPythonAssignment(
        name: Matcher<String>,
        expression: Matcher<PythonExpressionNode> = anything
    ): Matcher<PythonStatementNode> {
        return cast(allOf(
            has(PythonAssignmentNode::name, name),
            has(PythonAssignmentNode::expression, expression)
        ))
    }

    private fun isPythonBooleanLiteral(value: Boolean)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonBooleanLiteralNode::value, equalTo(value)))

    private fun isPythonIntegerLiteral(value: Int)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonIntegerLiteralNode::value, equalTo(value)))

    private fun isPythonStringLiteral(value: String)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonStringLiteralNode::value, equalTo(value)))

    private fun isPythonVariableReference(name: String)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonVariableReferenceNode::name, equalTo(name)))

    private fun isPythonBinaryOperation(
        operator: Matcher<PythonOperator>,
        left: Matcher<PythonExpressionNode>,
        right: Matcher<PythonExpressionNode>
    ) : Matcher<PythonExpressionNode>
    = cast(allOf(
        has(PythonBinaryOperationNode::operator, operator),
        has(PythonBinaryOperationNode::left, left),
        has(PythonBinaryOperationNode::right, right)
    ))

    private fun isPythonFunctionCall(
        function: Matcher<PythonExpressionNode>,
        arguments: Matcher<List<PythonExpressionNode>>
    ) : Matcher<PythonExpressionNode>
    = cast(allOf(
        has(PythonFunctionCallNode::function, function),
        has(PythonFunctionCallNode::arguments, arguments)
    ))
}
