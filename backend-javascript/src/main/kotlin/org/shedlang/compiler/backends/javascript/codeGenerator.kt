package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.FrontEndResult
import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.javascript.ast.*

internal fun generateCode(module: Module, modules: FrontEndResult): JavascriptModuleNode {
    val node = module.node
    val imports = node.imports.map({ importNode -> generateCode(module, importNode, modules) })
    val body = node.body.flatMap(::generateCode)
    val exports = node.body.filterIsInstance<VariableBindingNode>()
        .map(::generateExport)
    return JavascriptModuleNode(
        imports + body + exports,
        source = NodeSource(node)
    )
}

private fun generateCode(module: Module, import: ImportNode, modules: FrontEndResult): JavascriptStatementNode {
    val source = NodeSource(import)

    val importBase = when (import.path.base) {
        ImportPathBase.Relative -> "./"
        ImportPathBase.Absolute -> "./" + "../".repeat(module.name.size - 1)
    }

    val importPath = importBase + import.path.parts.joinToString("/")

    return JavascriptConstNode(
        name = import.name,
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
                statement.name,
                source = NodeSource(statement)
            ),
            JavascriptVariableReferenceNode(statement.name, source = NodeSource(statement)),
            source = NodeSource(statement)
        ),
        source = NodeSource(statement)
    )
}

internal fun generateCode(node: ModuleStatementNode): List<JavascriptStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<JavascriptStatementNode>> {
        override fun visit(node: ShapeNode): List<JavascriptStatementNode> = listOf(generateCode(node))
        override fun visit(node: UnionNode): List<JavascriptStatementNode> = listOf(generateCode(node))
        override fun visit(node: FunctionDeclarationNode): List<JavascriptStatementNode> = listOf(generateCode(node))
        override fun visit(node: ValNode): List<JavascriptStatementNode> = listOf(generateCode(node))
    })
}

private fun generateCode(node: ShapeNode) : JavascriptStatementNode {
    val source = NodeSource(node)
    return JavascriptConstNode(
        name = node.name,
        expression = JavascriptFunctionCallNode(
            JavascriptVariableReferenceNode(
                "\$shed.declareShape",
                source = source
            ),
            listOf(JavascriptStringLiteralNode(node.name, source = source)),
            source = source
        ),
        source = source
    )
}

private fun generateCode(node: UnionNode) : JavascriptStatementNode {
    val source = NodeSource(node)
    return JavascriptConstNode(
        name = node.name,
        expression = JavascriptNullLiteralNode(source = source),
        source = source
    )
}

private fun generateCode(node: FunctionDeclarationNode): JavascriptFunctionDeclarationNode {
    val javascriptFunction = generateFunction(node)
    return JavascriptFunctionDeclarationNode(
        name = node.name,
        arguments = javascriptFunction.arguments,
        body = javascriptFunction.body,
        source = NodeSource(node)
    )
}

private fun generateFunction(node: FunctionNode): JavascriptFunctionNode {
    val positionalParameters = node.arguments.map(ArgumentNode::name)
    val namedParameterName = "\$named"
    val namedParameters = if (node.namedParameters.isEmpty()) {
        listOf()
    } else {
        listOf(namedParameterName)
    }
    val namedParameterAssignments = node.namedParameters.map { parameter ->
        JavascriptConstNode(
            name = parameter.name,
            expression = JavascriptPropertyAccessNode(
                receiver = JavascriptVariableReferenceNode(
                    name = namedParameterName,
                    source = NodeSource(parameter)
                ),
                propertyName = parameter.name,
                source = NodeSource(parameter)
            ),
            source = NodeSource(parameter)
        )
    }
    val body = namedParameterAssignments + generateCode(node.body.statements)

    return object: JavascriptFunctionNode {
        override val arguments = positionalParameters + namedParameters
        override val body = body
    }
}

private fun generateCode(statements: List<StatementNode>): List<JavascriptStatementNode> {
    return statements.map(::generateCode)
}

internal fun generateCode(node: StatementNode): JavascriptStatementNode {
    return node.accept(object : StatementNode.Visitor<JavascriptStatementNode> {
        override fun visit(node: ExpressionStatementNode): JavascriptStatementNode {
            val expression = generateCode(node.expression)
            val source = NodeSource(node)
            return if (node.isReturn) {
                JavascriptReturnNode(expression, source)
            } else {
                JavascriptExpressionStatementNode(expression, source)
            }
        }

        override fun visit(node: ValNode): JavascriptStatementNode {
            return generateCode(node)
        }
    })
}

private fun generateCode(node: ValNode): JavascriptConstNode {
    return JavascriptConstNode(
        name = node.name,
        expression = generateCode(node.expression),
        source = NodeSource(node)
    )
}

internal fun generateCode(node: ExpressionNode): JavascriptExpressionNode {
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

        override fun visit(node: VariableReferenceNode): JavascriptExpressionNode {
            return generateCodeForReferenceNode(node)
        }

        override fun visit(node: BinaryOperationNode): JavascriptExpressionNode {
            return JavascriptBinaryOperationNode(
                operator = generateCode(node.operator),
                left = generateCode(node.left),
                right = generateCode(node.right),
                source = NodeSource(node)
            )
        }

        override fun visit(node: IsNode): JavascriptExpressionNode {
            return generateTypeCondition(
                expression = generateCode(node.expression),
                type = node.type,
                source = NodeSource(node)
            )
        }

        override fun visit(node: CallNode): JavascriptExpressionNode {
            val positionalArguments = node.positionalArguments.map(::generateCode)
            val namedArguments = if (node.namedArguments.isEmpty()) {
                listOf()
            } else {
                listOf(JavascriptObjectLiteralNode(
                    node.namedArguments.associate({ argument ->
                        argument.name to generateCode(argument.expression)
                    }),
                    source = NodeSource(node)
                ))
            }
            val arguments = positionalArguments + namedArguments
            return JavascriptFunctionCallNode(
                generateCode(node.receiver),
                arguments,
                source = NodeSource(node)
            )
        }

        override fun visit(node: PartialCallNode): JavascriptExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): JavascriptExpressionNode {
            return JavascriptPropertyAccessNode(
                generateCode(node.receiver),
                node.fieldName,
                source = NodeSource(node)
            )
        }

        override fun visit(node: FunctionExpressionNode): JavascriptExpressionNode {
            val javascriptFunction = generateFunction(node)
            return JavascriptFunctionExpressionNode(
                arguments = javascriptFunction.arguments,
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
                            condition = generateCode(branch.condition),
                            body = generateCode(branch.body),
                            source = NodeSource(branch)
                        )
                    },
                    elseBranch = generateCode(node.elseBranch),
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
                    body = generateCode(branch.body),
                    source = NodeSource(branch)
                )
            }

            return immediatelyInvokedFunction(
                body = listOf(
                    JavascriptConstNode(
                        name = temporaryName,
                        expression = generateCode(node.expression),
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
        arguments = listOf(),
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
                node.fieldName,
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
    return JavascriptVariableReferenceNode(node.name, NodeSource(node))
}
