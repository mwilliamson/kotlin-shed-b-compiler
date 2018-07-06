package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.ExpressionTypes
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.types.FunctionType
import org.shedlang.compiler.types.Type

internal fun generateCode(module: Module.Shed, modules: ModuleSet): JavascriptModuleNode {
    val context = CodeGenerationContext(expressionTypes = module.expressionTypes)

    val node = module.node
    val imports = node.imports.map({ importNode -> generateCode(module, importNode) })
    val body = node.body.flatMap { statement -> generateCode(statement, context)  }
    val exports = node.body.filterIsInstance<VariableBindingNode>()
        .map(::generateExport)
    return JavascriptModuleNode(
        imports + body + exports,
        source = NodeSource(node)
    )
}

internal class CodeGenerationContext(private val expressionTypes: ExpressionTypes) {
    fun typeOfExpression(node: ExpressionNode): Type {
        return expressionTypes.typeOf(node)
    }
}

private fun generateCode(module: Module.Shed, import: ImportNode): JavascriptStatementNode {
    val source = NodeSource(import)

    val importBase = when (import.path.base) {
        ImportPathBase.Relative -> "./"
        ImportPathBase.Absolute -> "./" + "../".repeat(module.name.size - 1)
    }

    val importPath = importBase + import.path.parts.map(Identifier::value).joinToString("/")

    return JavascriptConstNode(
        name = generateName(import.name),
        expression = JavascriptFunctionCallNode(
            JavascriptVariableReferenceNode("require", source = source),
            listOf(JavascriptStringLiteralNode(importPath, source = source)),
            source = source
        ),
        source = source
    )
}

private fun generateExport(statement: VariableBindingNode): JavascriptExpressionStatementNode {
    return JavascriptExpressionStatementNode(
        JavascriptAssignmentNode(
            JavascriptPropertyAccessNode(
                JavascriptVariableReferenceNode("exports", source = NodeSource(statement)),
                generateName(statement.name),
                source = NodeSource(statement)
            ),
            JavascriptVariableReferenceNode(generateName(statement.name), source = NodeSource(statement)),
            source = NodeSource(statement)
        ),
        source = NodeSource(statement)
    )
}

internal fun generateCode(node: ModuleStatementNode, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<JavascriptStatementNode>> {
        override fun visit(node: ShapeNode): List<JavascriptStatementNode> = listOf(generateCode(node, context))
        override fun visit(node: UnionNode): List<JavascriptStatementNode> = listOf(generateCode(node))
        override fun visit(node: FunctionDeclarationNode): List<JavascriptStatementNode> = listOf(generateCode(node, context))
        override fun visit(node: ValNode): List<JavascriptStatementNode> = listOf(generateCode(node, context))
    })
}

private fun generateCode(node: ShapeNode, context: CodeGenerationContext) : JavascriptStatementNode {
    val source = NodeSource(node)
    return JavascriptConstNode(
        name = generateName(node.name),
        expression = JavascriptFunctionCallNode(
            JavascriptVariableReferenceNode(
                "\$shed.declareShape",
                source = source
            ),
            listOf(
                JavascriptStringLiteralNode(generateName(node.name), source = source),
                JavascriptObjectLiteralNode(
                    node.fields.filter { field -> field.value != null }.associateBy(
                        { field -> generateName(field.name) },
                        { field -> generateCode(field.value!!, context) }
                    ),
                    source = source
                )
            ),
            source = source
        ),
        source = source
    )
}

private fun generateCode(node: UnionNode) : JavascriptStatementNode {
    val source = NodeSource(node)
    return JavascriptConstNode(
        name = generateName(node.name),
        expression = JavascriptNullLiteralNode(source = source),
        source = source
    )
}

private fun generateCode(node: FunctionDeclarationNode, context: CodeGenerationContext): JavascriptFunctionDeclarationNode {
    val javascriptFunction = generateFunction(node, context)
    return JavascriptFunctionDeclarationNode(
        name = generateName(node.name),
        parameters = javascriptFunction.parameters,
        body = javascriptFunction.body,
        source = NodeSource(node)
    )
}

private fun generateFunction(node: FunctionNode, context: CodeGenerationContext): JavascriptFunctionNode {
    val positionalParameters = node.parameters.map(ParameterNode::name).map(Identifier::value)
    val namedParameterName = "\$named"
    val namedParameters = if (node.namedParameters.isEmpty()) {
        listOf()
    } else {
        listOf(namedParameterName)
    }
    val namedParameterAssignments = node.namedParameters.map { parameter ->
        JavascriptConstNode(
            name = generateName(parameter.name),
            expression = JavascriptPropertyAccessNode(
                receiver = JavascriptVariableReferenceNode(
                    name = namedParameterName,
                    source = NodeSource(parameter)
                ),
                propertyName = generateName(parameter.name),
                source = NodeSource(parameter)
            ),
            source = NodeSource(parameter)
        )
    }
    val body = namedParameterAssignments + generateCode(node.body.statements, context)

    return object: JavascriptFunctionNode {
        override val parameters = positionalParameters + namedParameters
        override val body = body
    }
}

private fun generateCode(statements: List<StatementNode>, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return statements.map { statement -> generateCode(statement, context) }
}

internal fun generateCode(node: StatementNode, context: CodeGenerationContext): JavascriptStatementNode {
    return node.accept(object : StatementNode.Visitor<JavascriptStatementNode> {
        override fun visit(node: ExpressionStatementNode): JavascriptStatementNode {
            val expression = generateCode(node.expression, context)
            val source = NodeSource(node)
            return if (node.isReturn) {
                JavascriptReturnNode(expression, source)
            } else {
                JavascriptExpressionStatementNode(expression, source)
            }
        }

        override fun visit(node: ValNode): JavascriptStatementNode {
            return generateCode(node, context)
        }
    })
}

private fun generateCode(node: ValNode, context: CodeGenerationContext): JavascriptConstNode {
    return JavascriptConstNode(
        name = generateName(node.name),
        expression = generateCode(node.expression, context),
        source = NodeSource(node)
    )
}

internal fun generateCode(node: ExpressionNode, context: CodeGenerationContext): JavascriptExpressionNode {
    return node.accept(object : ExpressionNode.Visitor<JavascriptExpressionNode> {
        override fun visit(node: UnitLiteralNode): JavascriptExpressionNode {
            return JavascriptNullLiteralNode(NodeSource(node))
        }

        override fun visit(node: BooleanLiteralNode): JavascriptExpressionNode {
            return JavascriptBooleanLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: IntegerLiteralNode): JavascriptExpressionNode {
            return JavascriptIntegerLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: StringLiteralNode): JavascriptExpressionNode {
            return JavascriptStringLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: CharacterLiteralNode): JavascriptExpressionNode {
            val value = Character.toChars(node.value).joinToString()
            return JavascriptStringLiteralNode(value, NodeSource(node))
        }

        override fun visit(node: SymbolNode): JavascriptExpressionNode {
            return JavascriptFunctionCallNode(
                JavascriptVariableReferenceNode("_symbol", source = NodeSource(node)),
                listOf(JavascriptStringLiteralNode(node.name, source = NodeSource(node))),
                source = NodeSource(node)
            )
        }

        override fun visit(node: VariableReferenceNode): JavascriptExpressionNode {
            return generateCodeForReferenceNode(node)
        }

        override fun visit(node: BinaryOperationNode): JavascriptExpressionNode {
            return JavascriptBinaryOperationNode(
                operator = generateCode(node.operator),
                left = generateCode(node.left, context),
                right = generateCode(node.right, context),
                source = NodeSource(node)
            )
        }

        override fun visit(node: IsNode): JavascriptExpressionNode {
            return generateTypeCondition(
                expression = generateCode(node.expression, context),
                type = node.type,
                source = NodeSource(node)
            )
        }

        override fun visit(node: CallNode): JavascriptExpressionNode {
            val positionalArguments = node.positionalArguments.map { argument ->
                generateCode(argument, context)
            }
            val namedArguments = if (node.namedArguments.isEmpty()) {
                listOf()
            } else {
                listOf(JavascriptObjectLiteralNode(
                    node.namedArguments.associate({ argument ->
                        generateName(argument.name) to generateCode(argument.expression, context)
                    }),
                    source = NodeSource(node)
                ))
            }
            val arguments = positionalArguments + namedArguments
            return JavascriptFunctionCallNode(
                generateCode(node.receiver, context),
                arguments,
                source = NodeSource(node)
            )
        }

        override fun visit(node: PartialCallNode): JavascriptExpressionNode {
            val receiver = generateCode(node.receiver, context)
            val positionalArguments = node.positionalArguments.map { argument ->
                generateCode(argument, context)
            }
            val namedArguments = node.namedArguments
                .sortedBy { argument -> argument.name }
                .map { argument -> argument.name to generateCode(argument.expression, context) }

            val functionType = context.typeOfExpression(node.receiver) as FunctionType

            val partialArguments = listOf(receiver) +
                positionalArguments +
                namedArguments.map { argument -> argument.second }

            val partialParameters = listOf("\$func") +
                positionalArguments.indices.map { index -> "\$arg" + index } +
                namedArguments.map { argument -> generateName(argument.first) }

            val remainingPositionalParameters = (positionalArguments.size..functionType.positionalParameters.size - 1)
                .map { index -> "\$arg" + index }
            val remainingNamedParameters = functionType.namedParameters.keys - node.namedArguments.map { argument -> argument.name }
            val remainingParameters = remainingPositionalParameters + if (remainingNamedParameters.isEmpty()) {
                listOf()
            } else {
                listOf("\$named")
            }

            val finalNamedArgument = if (functionType.namedParameters.isEmpty()) {
                listOf()
            } else {
                listOf(JavascriptObjectLiteralNode(
                    properties = functionType.namedParameters
                        .keys
                        .sorted()
                        .associateBy(
                            { parameter -> generateName(parameter) },
                            { parameter ->
                                if (node.namedArguments.any { argument -> argument.name == parameter }) {
                                    JavascriptVariableReferenceNode(generateName(parameter), source = NodeSource(node))
                                } else {
                                    JavascriptPropertyAccessNode(
                                        receiver = JavascriptVariableReferenceNode(
                                            "\$named",
                                            source = NodeSource(node)
                                        ),
                                        propertyName = generateName(parameter),
                                        source = NodeSource(node)
                                    )
                                }
                            }
                        ),
                    source = NodeSource(node)
                ))
            }
            val fullArguments = functionType.positionalParameters.indices.map { index ->
                JavascriptVariableReferenceNode("\$arg" + index, source = NodeSource(node))
            } + finalNamedArgument

            return JavascriptFunctionCallNode(
                function = JavascriptFunctionExpressionNode(
                    parameters = partialParameters,
                    body = listOf(
                        JavascriptReturnNode(
                            expression = JavascriptFunctionExpressionNode(
                                parameters = remainingParameters,
                                body = listOf(
                                    JavascriptReturnNode(
                                        expression = JavascriptFunctionCallNode(
                                            function = JavascriptVariableReferenceNode(
                                                name = "\$func",
                                                source = NodeSource(node)
                                            ),
                                            arguments = fullArguments,
                                            source = NodeSource(node)
                                        ),
                                        source = NodeSource(node)
                                    )
                                ),
                                source = NodeSource(node)
                            ),
                            source = NodeSource(node)
                        )
                    ),
                    source = NodeSource(node)
                ),
                arguments = partialArguments,
                source = NodeSource(node)
            )
        }

        override fun visit(node: FieldAccessNode): JavascriptExpressionNode {
            return JavascriptPropertyAccessNode(
                generateCode(node.receiver, context),
                generateName(node.fieldName.identifier),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FunctionExpressionNode): JavascriptExpressionNode {
            val javascriptFunction = generateFunction(node, context)
            return JavascriptFunctionExpressionNode(
                parameters = javascriptFunction.parameters,
                body = javascriptFunction.body,
                source = node.source
            )
        }

        override fun visit(node: IfNode): JavascriptExpressionNode {
            val source = NodeSource(node)

            val body = listOf(
                JavascriptIfStatementNode(
                    conditionalBranches = node.conditionalBranches.map { branch ->
                        JavascriptConditionalBranchNode(
                            condition = generateCode(branch.condition, context),
                            body = generateCode(branch.body, context),
                            source = NodeSource(branch)
                        )
                    },
                    elseBranch = generateCode(node.elseBranch, context),
                    source = source
                )
            )

            return immediatelyInvokedFunction(body = body, source = source)
        }

        override fun visit(node: WhenNode): JavascriptExpressionNode {
            val source = NodeSource(node)
            val temporaryName = "\$shed_tmp"

            val branches = node.branches.map { branch ->
                val expression = NodeSource(branch)
                val condition = generateTypeCondition(JavascriptVariableReferenceNode(
                    name = temporaryName,
                    source = expression
                ), branch.type, NodeSource(branch))
                JavascriptConditionalBranchNode(
                    condition = condition,
                    body = generateCode(branch.body, context),
                    source = NodeSource(branch)
                )
            }

            return immediatelyInvokedFunction(
                body = listOf(
                    JavascriptConstNode(
                        name = temporaryName,
                        expression = generateCode(node.expression, context),
                        source = source
                    ),
                    JavascriptIfStatementNode(
                        conditionalBranches = branches,
                        elseBranch = listOf(),
                        source = source
                    )
                ),
                source = source
            )
        }

        private fun generateTypeCondition(
            expression: JavascriptExpressionNode,
            type: StaticNode,
            source: NodeSource
        ): JavascriptFunctionCallNode {
            return JavascriptFunctionCallNode(
                JavascriptVariableReferenceNode(
                    name = "\$shed.isType",
                    source = source
                ),
                listOf(
                    expression,
                    generateCode(type)
                ),
                source = source
            )
        }
    })
}

private fun immediatelyInvokedFunction(
    body: List<JavascriptStatementNode>,
    source: Source
): JavascriptExpressionNode {
    val function = JavascriptFunctionExpressionNode(
        parameters = listOf(),
        body = body,
        source = source
    )
    return JavascriptFunctionCallNode(
        function = function,
        arguments = listOf(),
        source = source
    )

}

private fun generateCode(operator: Operator): JavascriptOperator {
    return when (operator) {
        Operator.EQUALS -> JavascriptOperator.EQUALS
        Operator.LESS_THAN -> JavascriptOperator.LESS_THAN
        Operator.LESS_THAN_OR_EQUAL -> JavascriptOperator.LESS_THAN_OR_EQUAL
        Operator.GREATER_THAN -> JavascriptOperator.GREATER_THAN
        Operator.GREATER_THAN_OR_EQUAL -> JavascriptOperator.GREATER_THAN_OR_EQUAL
        Operator.ADD -> JavascriptOperator.ADD
        Operator.SUBTRACT -> JavascriptOperator.SUBTRACT
        Operator.MULTIPLY -> JavascriptOperator.MULTIPLY

    }
}

private fun generateCode(node: StaticNode): JavascriptExpressionNode {
    // TODO: test this
    return node.accept(object : StaticNode.Visitor<JavascriptExpressionNode> {
        override fun visit(node: StaticReferenceNode): JavascriptExpressionNode {
            return generateCodeForReferenceNode(node)
        }

        override fun visit(node: StaticFieldAccessNode): JavascriptExpressionNode {
            return JavascriptPropertyAccessNode(
                generateCode(node.receiver),
                generateName(node.fieldName),
                source = NodeSource(node)
            )
        }

        override fun visit(node: StaticApplicationNode): JavascriptExpressionNode {
            return generateCode(node.receiver)
        }

        override fun visit(node: FunctionTypeNode): JavascriptExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

private fun generateCodeForReferenceNode(node: ReferenceNode): JavascriptExpressionNode {
    return JavascriptVariableReferenceNode(generateName(node.name), NodeSource(node))
}

private fun generateName(identifier: Identifier): String {
    if (isJavascriptKeyword(identifier.value)) {
        return identifier.value + "_"
    } else {
        return identifier.value
    }
}

fun isJavascriptKeyword(value: String): Boolean {
    return value == "null"
}
