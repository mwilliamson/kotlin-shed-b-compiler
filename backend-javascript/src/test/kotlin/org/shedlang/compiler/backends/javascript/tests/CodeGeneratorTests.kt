package org.shedlang.compiler.backends.javascript.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.ast.StatementNode
import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.backends.javascript.generateCode
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.types.ModuleType
import java.nio.file.Paths

class CodeGeneratorTests {
    private val emptyModules = FrontEndResult(modules = listOf())

    @Test
    fun emptyModuleGeneratesEmptyModule() {
        val shed = stubbedModule(
            node = module(body = listOf())
        )
        val node = generateCode(shed, emptyModules)

        assertThat(node, isJavascriptModule(equalTo(listOf())))
    }

    @Test
    fun moduleImportsGenerateJavascriptImports() {
        val shed = stubbedModule(node = module(imports = listOf(import(ImportPath.relative(listOf("x"))))))

        val node = generateCode(shed, emptyModules)

        assertThat(node, isJavascriptModule(
            body = isSequence(
                isJavascriptConst(
                    name = equalTo("x"),
                    expression = isJavascriptFunctionCall(
                        isJavascriptVariableReference("require"),
                        isSequence(isJavascriptStringLiteral("./x"))
                    )
                )
            )
        ))
    }

    @Test
    fun moduleIncludesBodyAndExports() {
        val shed = stubbedModule(node = module(body = listOf(function(name = "f"))))

        val node = generateCode(shed, emptyModules)

        assertThat(node, isJavascriptModule(
            body = isSequence(
                isJavascriptFunction(name = equalTo("f")),
                isJavascriptAssignmentStatement(
                    isJavascriptPropertyAccess(
                        isJavascriptVariableReference("exports"),
                        equalTo("f")
                    ),
                    isJavascriptVariableReference("f")
                )
            )
        ))
    }

    private fun stubbedModule(node: ModuleNode): Module {
        return Module(
            name = listOf("Module"),
            sourcePath = Paths.get("Module.shed"),
            type = ModuleType(mapOf()),
            references = ResolvedReferencesMap(mapOf()),
            node = node
        )
    }

    @Test
    fun shapeGeneratesType() {
        val shed = shape(name = "X")

        val node = generateCode(shed).single()

        assertThat(node, isJavascriptConst(
            name = equalTo("X"),
            expression = isJavascriptFunctionCall(
                isJavascriptVariableReference("\$shed.declareShape"),
                isSequence(isJavascriptStringLiteral("X"))
            )
        ))
    }

    @Test
    fun unionGeneratesStub() {
        val shed = union("X", listOf())

        val node = generateCode(shed).single()

        assertThat(node, isJavascriptConst(
            name = equalTo("X"),
            expression = isJavascriptNull()
        ))
    }

    @Test
    fun functionDeclarationGeneratesFunctionDeclaration() {
        val shed = function(
            name = "f",
            arguments = listOf(argument("x"), argument("y")),
            body = listOf(expressionStatement(literalInt(42)))
        )

        val node = generateCode(shed)

        assertThat(node.single(), isJavascriptFunction(
            name = equalTo("f"),
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isSequence(isJavascriptExpressionStatement(isJavascriptIntegerLiteral(42)))
        ))
    }

    @Test
    fun functionExpressionGeneratesFunctionExpression() {
        val shed = functionExpression(
            arguments = listOf(argument("x"), argument("y")),
            body = listOf(expressionStatement(literalInt(42)))
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptFunctionExpression(
            arguments = isSequence(equalTo("x"), equalTo("y")),
            body = isSequence(isJavascriptExpressionStatement(isJavascriptIntegerLiteral(42)))
        ))
    }

    @Test
    fun nonReturningExpressionStatementGeneratesExpressionStatement() {
        val shed = expressionStatement(literalInt(42), isReturn = false)

        val node = generateCode(shed)

        assertThat(node, cast(has(
            JavascriptExpressionStatementNode::expression,
            isJavascriptIntegerLiteral(42)
        )))
    }

    @Test
    fun returningExpressionStatementGeneratesReturnStatement() {
        val shed = expressionStatement(literalInt(42), isReturn = true)

        val node = generateCode(shed)

        assertThat(node, cast(has(
            JavascriptReturnNode::expression,
            isJavascriptIntegerLiteral(42)
        )))
    }

    @Test
    fun ifExpressionGeneratesImmediatelyEvaluatedIfStatement() {
        val shed = ifExpression(
            literalInt(42),
            listOf(expressionStatement(literalInt(0))),
            listOf(expressionStatement(literalInt(1)))
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptImmediatelyInvokedFunction(
            body = isSequence(
                isJavascriptIfStatement(
                    conditionalBranches = isSequence(
                        isJavascriptConditionalBranch(
                            condition = isJavascriptIntegerLiteral(42),
                            body = isSequence(
                                isJavascriptExpressionStatement(isJavascriptIntegerLiteral(0))
                            )
                        )
                    ),
                    elseBranch = isSequence(
                        isJavascriptExpressionStatement(isJavascriptIntegerLiteral(1))
                    )
                )
            )
        ))
    }

    @Test
    fun whenExpressionGeneratesImmediatelyEvaluatedIfStatement() {
        val shed = whenExpression(
            variableReference("x"),
            listOf(
                whenBranch(
                    staticReference("T"),
                    listOf(
                        expressionStatement(literalInt(42), isReturn = true)
                    )
                )
            )
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptImmediatelyInvokedFunction(
            body = isSequence(
                isJavascriptConst(
                    name = equalTo("\$shed_tmp"),
                    expression = isJavascriptVariableReference("x")
                ),
                isJavascriptIfStatement(
                    conditionalBranches = isSequence(
                        isJavascriptConditionalBranch(
                            condition = isJavascriptTypeCondition(
                                expression = isJavascriptVariableReference("\$shed_tmp"),
                                type = isJavascriptVariableReference("T")
                            ),
                            body = isSequence(
                                isJavascriptReturn(isJavascriptIntegerLiteral(42))
                            )
                        )
                    ),
                    elseBranch = isSequence()
                )
            )
        ))
    }

    @Test
    fun valGeneratesConst() {
        val shed = valStatement(name = "x", expression = literalBool(true))

        val node = generateCode(shed as StatementNode)

        assertThat(node, isJavascriptConst(equalTo("x"), isJavascriptBooleanLiteral(true)))
    }

    @Test
    fun unitLiteralGeneratesNull() {
        val shed = literalUnit()
        val node = generateCode(shed)
        assertThat(node, isJavascriptNull())
    }

    @Test
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, isJavascriptBooleanLiteral(true))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, isJavascriptIntegerLiteral(42))
    }

    @Test
    fun stringLiteralGeneratesStringLiteral() {
        val shed = literalString("<string>")
        val node = generateCode(shed)
        assertThat(node, isJavascriptStringLiteral("<string>"))
    }

    @Test
    fun variableReferenceGenerateVariableReference() {
        val shed = variableReference("x")

        val node = generateCode(shed)

        assertThat(node, isJavascriptVariableReference("x"))
    }

    @TestFactory
    fun binaryOperationGeneratesBinaryOperation(): List<DynamicTest> {
        return listOf(
            Operator.ADD to JavascriptOperator.ADD,
            Operator.SUBTRACT to JavascriptOperator.SUBTRACT,
            Operator.MULTIPLY to JavascriptOperator.MULTIPLY,
            Operator.EQUALS to JavascriptOperator.EQUALS
        ).map({ operator ->  DynamicTest.dynamicTest(
            operator.first.toString(), {
                val shed = binaryOperation(
                    operator = operator.first,
                    left = literalInt(0),
                    right = literalInt(1)
                )

                val node = generateCode(shed)

                assertThat(node, isJavascriptBinaryOperation(
                    operator = equalTo(operator.second),
                    left = isJavascriptIntegerLiteral(0),
                    right = isJavascriptIntegerLiteral(1)
                ))
            })
        })
    }

    @Test
    fun isOperationGeneratesTypeCheck() {
        val reference = variableReference("x")
        val typeReference = staticReference("X")

        val shed = isOperation(
            expression = reference,
            type = typeReference
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptTypeCondition(isJavascriptVariableReference("x"), isJavascriptVariableReference("X")))
    }

    @Test
    fun functionCallGeneratesFunctionCall() {
        val shed = call(variableReference("f"), listOf(literalInt(42)))

        val node = generateCode(shed)

        assertThat(node, isJavascriptFunctionCall(
            isJavascriptVariableReference("f"),
            isSequence(isJavascriptIntegerLiteral(42))
        ))
    }

    @Test
    fun namedArgumentsArePassedAsObject() {
        val shed = call(
            variableReference("f"),
            namedArguments = listOf(callNamedArgument("a", literalBool(true)))
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptFunctionCall(
            isJavascriptVariableReference("f"),
            isSequence(isJavascriptObject(isMap("a" to isJavascriptBooleanLiteral(true))))
        ))
    }

    @Test
    fun whenThereAreBothPositionalAndNamedArgumentsThenNamedArgumentsObjectIsLastArgument() {
        val shed = call(
            variableReference("f"),
            positionalArguments = listOf(literalInt(1)),
            namedArguments = listOf(callNamedArgument("a", literalBool(true)))
        )

        val node = generateCode(shed)

        assertThat(node, isJavascriptFunctionCall(
            isJavascriptVariableReference("f"),
            isSequence(
                isJavascriptIntegerLiteral(1),
                isJavascriptObject(isMap("a" to isJavascriptBooleanLiteral(true)))
            )
        ))
    }

    @Test
    fun fieldAccessGeneratesPropertyAccess() {
        val shed = fieldAccess(variableReference("x"), "y")

        val node = generateCode(shed)

        assertThat(node, isJavascriptPropertyAccess(
            isJavascriptVariableReference("x"),
            equalTo("y")
        ))
    }

    private fun isJavascriptModule(body: Matcher<List<JavascriptStatementNode>>)
        = cast(has(JavascriptModuleNode::body, body))

    private fun isJavascriptFunction(
        name: Matcher<String>,
        arguments: Matcher<List<String>> = anything,
        body: Matcher<List<JavascriptStatementNode>> = anything
    ) : Matcher<JavascriptStatementNode>
        = cast(allOf(
            has(JavascriptFunctionDeclarationNode::name, name),
            has(JavascriptFunctionDeclarationNode::arguments, arguments),
            has(JavascriptFunctionDeclarationNode::body, body)
        ))

    private fun isJavascriptFunctionExpression(
        arguments: Matcher<List<String>> = anything,
        body: Matcher<List<JavascriptStatementNode>> = anything
    ) : Matcher<JavascriptExpressionNode>
        = cast(allOf(
        has(JavascriptFunctionExpressionNode::arguments, arguments),
        has(JavascriptFunctionExpressionNode::body, body)
    ))

    private fun isJavascriptConst(
        name: Matcher<String>,
        expression: Matcher<JavascriptExpressionNode>
    ): Matcher<JavascriptStatementNode>  = cast(allOf(
        has(JavascriptConstNode::name, name),
        has(JavascriptConstNode::expression, expression)
    ))

    private fun isJavascriptIfStatement(
        conditionalBranches: Matcher<List<JavascriptConditionalBranchNode>>,
        elseBranch: Matcher<List<JavascriptStatementNode>>
    ): Matcher<JavascriptStatementNode> {
        return cast(allOf(
            has(JavascriptIfStatementNode::conditionalBranches, conditionalBranches),
            has(JavascriptIfStatementNode::elseBranch, elseBranch)
        ))
    }

    private fun isJavascriptConditionalBranch(
        condition: Matcher<JavascriptExpressionNode>,
        body: Matcher<List<JavascriptStatementNode>>
    ): Matcher<JavascriptConditionalBranchNode> {
        return allOf(
            has(JavascriptConditionalBranchNode::condition, condition),
            has(JavascriptConditionalBranchNode::body, body)
        )
    }

    private fun isJavascriptReturn(expression: Matcher<JavascriptExpressionNode>)
        : Matcher<JavascriptStatementNode>
        = cast(has(JavascriptReturnNode::expression, expression))

    private fun isJavascriptExpressionStatement(expression: Matcher<JavascriptExpressionNode>)
        = cast(has(JavascriptExpressionStatementNode::expression, expression))

    private fun isJavascriptBooleanLiteral(value: Boolean)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptBooleanLiteralNode::value, equalTo(value)))

    private fun isJavascriptNull()
        : Matcher<JavascriptExpressionNode>
        = isA<JavascriptNullLiteralNode>()

    private fun isJavascriptIntegerLiteral(value: Int)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptIntegerLiteralNode::value, equalTo(value)))

    private fun isJavascriptStringLiteral(value: String)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptStringLiteralNode::value, equalTo(value)))

    private fun isJavascriptVariableReference(name: String)
        : Matcher<JavascriptExpressionNode>
        = cast(has(JavascriptVariableReferenceNode::name, equalTo(name)))

    private fun isJavascriptBinaryOperation(
        operator: Matcher<JavascriptOperator>,
        left: Matcher<JavascriptExpressionNode>,
        right: Matcher<JavascriptExpressionNode>
    ) : Matcher<JavascriptExpressionNode>
    = cast(allOf(
        has(JavascriptBinaryOperationNode::operator, operator),
        has(JavascriptBinaryOperationNode::left, left),
        has(JavascriptBinaryOperationNode::right, right)
    ))

    private fun isJavascriptFunctionCall(
        function: Matcher<JavascriptExpressionNode>,
        arguments: Matcher<List<JavascriptExpressionNode>>
    ) : Matcher<JavascriptExpressionNode>
    = cast(allOf(
        has(JavascriptFunctionCallNode::function, function),
        has(JavascriptFunctionCallNode::arguments, arguments)
    ))

    private fun isJavascriptPropertyAccess(
        receiver: Matcher<JavascriptExpressionNode>,
        propertyName: Matcher<String>
    ) : Matcher<JavascriptExpressionNode> = cast(allOf(
        has(JavascriptPropertyAccessNode::receiver, receiver),
        has(JavascriptPropertyAccessNode::propertyName, propertyName)
    ))

    private fun isJavascriptObject(
        properties: Matcher<Map<String, JavascriptExpressionNode>>
    ): Matcher<JavascriptExpressionNode> = cast(
        has(JavascriptObjectLiteralNode::properties, properties)
    )

    private fun isJavascriptAssignment(
        target: Matcher<JavascriptExpressionNode>,
        expression: Matcher<JavascriptExpressionNode>
    ): Matcher<JavascriptExpressionNode> = cast(allOf(
        has(JavascriptAssignmentNode::target, target),
        has(JavascriptAssignmentNode::expression, expression)
    ))

    private fun isJavascriptAssignmentStatement(
        target: Matcher<JavascriptExpressionNode>,
        expression: Matcher<JavascriptExpressionNode>
    ) = isJavascriptExpressionStatement(
        isJavascriptAssignment(target = target, expression = expression)
    )

    private fun isJavascriptTypeCondition(
        expression: Matcher<JavascriptExpressionNode>,
        type: Matcher<JavascriptExpressionNode>
    ): Matcher<JavascriptExpressionNode> {
        return isJavascriptFunctionCall(
            // TODO: should be a field access
            isJavascriptVariableReference("\$shed.isType"),
            isSequence(expression, type)
        )
    }

    private fun isJavascriptImmediatelyInvokedFunction(
        body: Matcher<List<JavascriptStatementNode>>
    ): Matcher<JavascriptExpressionNode> {
        return isJavascriptFunctionCall(
            function = isJavascriptFunctionExpression(
                arguments = isSequence(),
                body = body
            ),
            arguments = isSequence()
        )
    }
}
