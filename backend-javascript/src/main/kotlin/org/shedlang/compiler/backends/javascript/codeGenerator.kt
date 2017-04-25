package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.javascript.ast.*

internal fun generateCode(node: ModuleNode): JavascriptModuleNode {
    val body = node.body.flatMap(::generateCode)
    val exports = node.body.filterIsInstance<VariableBindingNode>()
        .map({ statement ->
            JavascriptExpressionStatementNode(
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
        })
    return JavascriptModuleNode(
        body + exports,
        source = NodeSource(node)
    )
}

internal fun generateCode(node: ModuleStatementNode): List<JavascriptStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<JavascriptStatementNode>> {
        override fun visit(node: ShapeNode): List<JavascriptStatementNode> = listOf(generateCode(node))
        override fun visit(node: UnionNode): List<JavascriptStatementNode> = listOf(generateCode(node))
        override fun visit(node: FunctionNode): List<JavascriptStatementNode> = listOf(generateCode(node))
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

private fun generateCode(node: FunctionNode): JavascriptFunctionNode {
    return JavascriptFunctionNode(
        name = node.name,
        arguments = node.arguments.map(ArgumentNode::name),
        body = node.body.map(::generateCode),
        source = NodeSource(node)
    )
}

internal fun generateCode(node: StatementNode): JavascriptStatementNode {
    return node.accept(object : StatementNode.Visitor<JavascriptStatementNode> {
        override fun visit(node: ReturnNode): JavascriptStatementNode {
            return JavascriptReturnNode(generateCode(node.expression), NodeSource(node))
        }

        override fun visit(node: IfStatementNode): JavascriptStatementNode {
            return JavascriptIfStatementNode(
                condition = generateCode(node.condition),
                trueBranch = node.trueBranch.map(::generateCode),
                falseBranch = node.falseBranch.map(::generateCode),
                source = NodeSource(node)
            )
        }

        override fun visit(node: ExpressionStatementNode): JavascriptStatementNode {
            return JavascriptExpressionStatementNode(generateCode(node.expression), NodeSource(node))
        }

        override fun visit(node: ValNode): JavascriptStatementNode {
            return JavascriptConstNode(
                name = node.name,
                expression = generateCode(node.expression),
                source = NodeSource(node)
            )
        }
    })
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
            return JavascriptFunctionCallNode(
                JavascriptVariableReferenceNode(
                    name = "\$shed.isType",
                    source = NodeSource(node)
                ),
                listOf(
                    generateCode(node.expression),
                    generateCode(node.type)
                ),
                source = NodeSource(node)
            )
        }

        override fun visit(node: CallNode): JavascriptExpressionNode {
            if (node.namedArguments.isEmpty()) {
                return JavascriptFunctionCallNode(
                    generateCode(node.receiver),
                    node.positionalArguments.map(::generateCode),
                    source = NodeSource(node)
                )
            } else {
                val fieldArguments = node.namedArguments.associate({ argument ->
                    argument.name to generateCode(argument.expression)
                })
                return JavascriptObjectLiteralNode(
                    mapOf("\$shedType" to generateCode(node.receiver)) + fieldArguments,
                    source = NodeSource(node)
                )
            }
        }

        override fun visit(node: FieldAccessNode): JavascriptExpressionNode {
            return JavascriptPropertyAccessNode(
                generateCode(node.receiver),
                node.fieldName,
                source = NodeSource(node)
            )
        }
    })
}

private fun generateCode(operator: Operator): JavascriptOperator {
    return when (operator) {
        Operator.EQUALS -> JavascriptOperator.EQUALS
        Operator.ADD -> JavascriptOperator.ADD
        Operator.SUBTRACT -> JavascriptOperator.SUBTRACT
        Operator.MULTIPLY -> JavascriptOperator.MULTIPLY

    }
}

private fun generateCode(node: TypeNode): JavascriptExpressionNode {
    // TODO: test this
    return node.accept(object : TypeNode.Visitor<JavascriptExpressionNode> {
        override fun visit(node: TypeReferenceNode): JavascriptExpressionNode {
            return generateCodeForReferenceNode(node)
        }
    })
}

private fun generateCodeForReferenceNode(node: ReferenceNode): JavascriptExpressionNode {
    return JavascriptVariableReferenceNode(node.name, NodeSource(node))
}
