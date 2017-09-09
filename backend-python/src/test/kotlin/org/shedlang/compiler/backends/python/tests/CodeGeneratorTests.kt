package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.CodeGenerationContext
import org.shedlang.compiler.backends.python.GeneratedCode
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.testing.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap

class CodeGeneratorTests {
    @Test
    fun emptyModuleGeneratesEmptyModule() {
        val shed = module(body = listOf())

        val node = generateCode(shed)

        assertThat(node, isPythonModule(equalTo(listOf())))
    }

    @Test
    fun moduleImportsGeneratePythonImports() {
        val shed = module(imports = listOf(import(ImportPath.relative(listOf("x", "y")))))

        val node = generateCode(shed)

        assertThat(node, isPythonModule(
            body = isSequence(
                isPythonImportFrom(
                    module = equalTo(".x"),
                    names = isSequence(equalTo("y"))
                )
            )
        ))
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
    fun shapeGeneratesClass() {
        val shed = shape(
            name = "X",
            fields = listOf(
                shapeField("a", staticReference("Int"))
            )
        )

        val node = generateCode(shed).single()

        assertThat(node, isPythonClass(
            name = equalTo("X"),
            body = isSequence(
                isPythonFunction(
                    name = equalTo("__init__"),
                    arguments = isSequence(equalTo("self"), equalTo("a")),
                    body = isSequence(
                        isPythonAssignment(
                            target = isPythonAttributeAccess(
                                receiver = isPythonVariableReference("self"),
                                attributeName = equalTo("a")
                            ),
                            expression = isPythonVariableReference("a")
                        )
                    )
                )
            )
        ))
    }

    @Test
    fun functionDeclarationGeneratesFunction() {
        val shed = function(
            name = "f",
            arguments = listOf(argument("x"), argument("y")),
            body = listOf(returns(literalInt(42)))
        )

        val node = generateCode(shed).single()

        assertThat(node, isPythonFunction(
            name = equalTo("f"),
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isSequence(isPythonReturn(isPythonIntegerLiteral(42)))
        ))
    }

    @Test
    fun functionExpressionWithNoStatementsGeneratesLambda() {
        val shed = functionExpression(
            arguments = listOf(argument("x"), argument("y")),
            body = listOf()
        )

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonLambda(
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isPythonNone()
        )))
    }

    @Test
    fun functionExpressionWithSingleReturnStatementGeneratesLambda() {
        val shed = functionExpression(
            arguments = listOf(argument("x"), argument("y")),
            body = listOf(returns(literalInt(42)))
        )

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonLambda(
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isPythonIntegerLiteral(42)
        )))
    }

    @Test
    fun functionExpressionWithNonEmptyBodyThatIsntSingleReturnGeneratesAuxiliaryFunction() {
        val shed = functionExpression(
            arguments = listOf(argument("x"), argument("y")),
            body = listOf(expressionStatement(literalInt(42)))
        )

        val node = generateCode(shed)
        val auxiliaryFunction = node.functions.single()
        assertThat(auxiliaryFunction, isPythonFunction(
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isSequence(isPythonExpressionStatement(isPythonIntegerLiteral(42)))
        ))

        assertThat(node.value, isPythonVariableReference(auxiliaryFunction.name))
    }

    @Test
    fun expressionStatementGeneratesExpressionStatement() {
        val shed = expressionStatement(literalInt(42))
        val node = generateCode(shed)
        assertThat(node, isSequence(isPythonExpressionStatement(isPythonIntegerLiteral(42))))
    }

    @Test
    fun returnStatementGeneratesReturnStatement() {
        val shed = returns(literalInt(42))
        val node = generateCode(shed)
        assertThat(node, isSequence(isPythonReturn(isPythonIntegerLiteral(42))))
    }

    @Test
    fun ifStatementGeneratesIfStatement() {
        val shed = ifStatement(
            literalInt(42),
            listOf(returns(literalInt(0))),
            listOf(returns(literalInt(1)))
        )

        val node = generateCode(shed)

        assertThat(node, isSequence(
            cast(allOf(
                has(PythonIfStatementNode::condition, isPythonIntegerLiteral(42)),
                has(PythonIfStatementNode::trueBranch, isSequence(
                    isPythonReturn(isPythonIntegerLiteral(0))
                )),
                has(PythonIfStatementNode::falseBranch, isSequence(
                    isPythonReturn(isPythonIntegerLiteral(1))
                ))
            ))
        ))
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
            isPythonAssignment(target = isPythonVariableReference("x")),
            cast(allOf(
                has(PythonIfStatementNode::trueBranch, isSequence(
                    isPythonAssignment(target = isPythonVariableReference("x_1")),
                    isPythonReturn(isPythonVariableReference("x")),
                    isPythonReturn(isPythonVariableReference("x_1"))
                )),
                has(PythonIfStatementNode::falseBranch, isSequence(
                    isPythonAssignment(target = isPythonVariableReference("x_2")),
                    isPythonReturn(isPythonVariableReference("x_2"))
                ))
            ))
        ))
    }

    @Test
    fun valGeneratesAssignment() {
        val shed = valStatement(name = "x", expression = literalInt(42))

        val node = generateCode(shed)

        assertThat(node, isSequence(
            isPythonAssignment(
                target = isPythonVariableReference("x"),
                expression = isPythonIntegerLiteral(42)
            )
        ))
    }

    @Test
    fun unitLiteralGeneratesNone() {
        val shed = literalUnit()
        val node = generateCode(shed)
        assertThat(node, isGeneratedExpression(isPythonNone()))
    }

    @Test
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonBooleanLiteral(true)))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonIntegerLiteral(42)))
    }

    @Test
    fun stringLiteralGeneratesStringLiteral() {
        val shed = literalString("<string>")
        val node = generateCode(shed)
        assertThat(node, isGeneratedExpression(isPythonStringLiteral("<string>")))
    }

    @Test
    fun variableReferenceGeneratesVariableReference() {
        val declaration = argument("x")
        val shed = variableReference("x")

        val node = generateCode(shed, context(mapOf(shed to declaration)))

        assertThat(node, isGeneratedExpression(isPythonVariableReference("x")))
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

                assertThat(node, isGeneratedExpression(isPythonBinaryOperation(
                    operator = equalTo(operator.second),
                    left = isPythonIntegerLiteral(0),
                    right = isPythonIntegerLiteral(1)
                )))
            })
        })
    }

    @Test
    fun isOperationGeneratesIsInstanceCall() {
        val declaration = argument("x")
        val reference = variableReference("x")
        val typeReference = staticReference("X")
        val typeDeclaration = shape("X", listOf())

        val shed = isOperation(
            expression = reference,
            type = typeReference
        )

        val context = context(mapOf(
            reference to declaration,
            typeReference to typeDeclaration
        ))
        val node = generateCode(shed, context)

        assertThat(node, isGeneratedExpression(isPythonFunctionCall(
            isPythonVariableReference("isinstance"),
            isSequence(isPythonVariableReference("x"), isPythonVariableReference("X"))
        )))
    }

    @Test
    fun functionCallGeneratesFunctionCall() {
        val declaration = argument("f")
        val function = variableReference("f")
        val shed = call(
            function,
            positionalArguments = listOf(literalInt(42)),
            namedArguments = listOf(callNamedArgument("x", literalBool(true)))
        )

        val node = generateCode(shed, context(mapOf(function to declaration)))

        assertThat(node, isGeneratedExpression(isPythonFunctionCall(
            isPythonVariableReference("f"),
            isSequence(isPythonIntegerLiteral(42)),
            isSequence(isPair(equalTo("x"), isPythonBooleanLiteral(true)))
        )))
    }

    @Test
    fun fieldAccessGeneratesAttributeAccess() {
        val declaration = argument("x")
        val receiver = variableReference("x")
        val shed = fieldAccess(receiver, "y")

        val node = generateCode(shed, context(mapOf(receiver to declaration)))

        assertThat(node, isGeneratedExpression(isPythonAttributeAccess(
            receiver = isPythonVariableReference("x"),
            attributeName = equalTo("y")
        )))
    }

    @Test
    fun namesHavePep8Casing() {
        assertThat(
            generateCode(valStatement(name = "oneTwoThree")),
            isSequence(
                isPythonAssignment(
                    target = isPythonVariableReference("one_two_three")
                )
            )
        )
        assertThat(
            generateCode(function(name = "oneTwoThree")),
            isSequence(
                isPythonFunction(name = equalTo("one_two_three"))
            )
        )
        assertThat(
            generateCode(shape(name = "OneTwoThree")),
            isSequence(
                isPythonClass(name = equalTo("OneTwoThree"))
            )
        )
    }

    private fun generateCode(node: ModuleNode) = generateCode(node, context())
    private fun generateCode(node: ShapeNode) = generateCode(node, context())
    private fun generateCode(node: FunctionDeclarationNode) = generateCode(node, context())
    private fun generateCode(node: StatementNode) = generateCode(node, context())
    private fun generateCode(node: ExpressionNode) = generateCode(node, context())

    private fun context(
        references: Map<ReferenceNode, VariableBindingNode> = mapOf()
    ) = CodeGenerationContext(
        references = ResolvedReferencesMap(references.entries.associate({ entry -> entry.key.nodeId to entry.value.nodeId}))
    )

    private fun isPythonModule(body: Matcher<List<PythonStatementNode>>)
        = cast(has(PythonModuleNode::body, body))

    private fun isPythonImportFrom(
        module: Matcher<String>,
        names: Matcher<List<String>>
    ) = cast(allOf(
        has(PythonImportFromNode::module, module),
        has(PythonImportFromNode::names, names)
    ))

    private fun isPythonClass(
        name: Matcher<String>,
        body: Matcher<List<PythonStatementNode>> = anything
    ) : Matcher<PythonStatementNode>
        = cast(allOf(
        has(PythonClassNode::name, name),
        has(PythonClassNode::body, body)
    ))

    private fun isPythonFunction(
        name: Matcher<String> = anything,
        arguments: Matcher<List<String>> = anything,
        body: Matcher<List<PythonStatementNode>> = anything
    ) : Matcher<PythonStatementNode>
        = cast(allOf(
            has(PythonFunctionNode::name, name),
            has(PythonFunctionNode::arguments, arguments),
            has(PythonFunctionNode::body, body)
        ))

    private fun isPythonLambda(
        arguments: Matcher<List<String>> = anything,
        body: Matcher<PythonExpressionNode> = anything
    ): Matcher<PythonExpressionNode> = cast(allOf(
        has(PythonLambdaNode::arguments, arguments),
        has(PythonLambdaNode::body, body)
    ))

    private fun isPythonReturn(expression: Matcher<PythonExpressionNode>)
        : Matcher<PythonStatementNode>
        = cast(has(PythonReturnNode::expression, expression))

    private fun isPythonExpressionStatement(expression: Matcher<PythonExpressionNode>)
        : Matcher<PythonStatementNode>
        = cast(has(PythonExpressionStatementNode::expression, expression))

    private fun isPythonAssignment(
        target: Matcher<PythonExpressionNode>,
        expression: Matcher<PythonExpressionNode> = anything
    ): Matcher<PythonStatementNode> {
        return cast(allOf(
            has(PythonAssignmentNode::target, target),
            has(PythonAssignmentNode::expression, expression)
        ))
    }

    private fun isPythonNone()
        : Matcher<PythonExpressionNode>
        = isA<PythonNoneLiteralNode>()

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
        arguments: Matcher<List<PythonExpressionNode>> = isSequence(),
        keywordArguments: Matcher<List<Pair<String, PythonExpressionNode>>> = isSequence()
    ) : Matcher<PythonExpressionNode>
    = cast(allOf(
        has(PythonFunctionCallNode::function, function),
        has(PythonFunctionCallNode::arguments, arguments),
        has(PythonFunctionCallNode::keywordArguments, keywordArguments)
    ))

    private fun isPythonAttributeAccess(
        receiver: Matcher<PythonExpressionNode>,
        attributeName: Matcher<String>
    ) : Matcher<PythonExpressionNode>
        = cast(allOf(
        has(PythonAttributeAccessNode::receiver, receiver),
        has(PythonAttributeAccessNode::attributeName, attributeName)
    ))

    private fun isGeneratedExpression(value: Matcher<PythonExpressionNode>) = allOf(
        has(GeneratedCode<PythonExpressionNode>::value, value),
        has(GeneratedCode<PythonExpressionNode>::functions, isEmpty)
    )
}
