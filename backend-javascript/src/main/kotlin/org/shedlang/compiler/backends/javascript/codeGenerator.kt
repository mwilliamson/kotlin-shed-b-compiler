package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.javascript.ast.*

internal fun generateCode(node: ModuleNode): JavascriptModuleNode {
    return JavascriptModuleNode(
        node.body.map(::generateCode),
        source = NodeSource(node)
    )
}

internal fun generateCode(node: ModuleStatementNode): JavascriptStatementNode {
    return node.accept(object : ModuleStatementNode.Visitor<JavascriptStatementNode> {
        override fun visit(node: ShapeNode): JavascriptStatementNode {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionNode): JavascriptStatementNode = generateCode(node)
    })
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
            return JavascriptVariableReferenceNode(node.name, NodeSource(node))
        }

        override fun visit(node: BinaryOperationNode): JavascriptExpressionNode {
            return JavascriptBinaryOperationNode(
                operator = generateCode(node.operator),
                left = generateCode(node.left),
                right = generateCode(node.right),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FunctionCallNode): JavascriptExpressionNode {
            return JavascriptFunctionCallNode(
                generateCode(node.function),
                node.arguments.map(::generateCode),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FieldAccessNode): JavascriptExpressionNode {
            throw UnsupportedOperationException("not implemented")
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
