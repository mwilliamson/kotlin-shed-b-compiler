package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.ast.*

fun generateCode(node: ModuleNode): PythonModuleNode {
    return PythonModuleNode(
        node.body.map(::generateCode),
        source = NodeSource(node)
    )
}

fun generateCode(node: FunctionNode): PythonFunctionNode {
    return PythonFunctionNode(
        name = node.name,
        arguments = node.arguments.map(ArgumentNode::name),
        body = node.body.map(::generateCode),
        source = NodeSource(node)
    )
}

fun generateCode(node: StatementNode): PythonStatementNode {
    return node.accept(object : StatementNode.Visitor<PythonStatementNode> {
        override fun visit(node: ReturnNode): PythonStatementNode {
            return PythonReturnNode(generateCode(node.expression), NodeSource(node))
        }

        override fun visit(node: IfStatementNode): PythonStatementNode {
            return PythonIfStatementNode(
                condition = generateCode(node.condition),
                trueBranch = node.trueBranch.map(::generateCode),
                falseBranch = node.falseBranch.map(::generateCode),
                source = NodeSource(node)
            )
        }

        override fun visit(node: ExpressionStatementNode): PythonStatementNode {
            return PythonExpressionStatementNode(generateCode(node.expression), NodeSource(node))
        }

        override fun visit(node: ValNode): PythonStatementNode {
            // TODO: handle scoping e.g.
            // val x = "1";
            // if (true) {
            //    val x = "2";
            // }
            // print(x) // Should print 1
            return PythonAssignmentNode(
                name = node.name,
                expression = generateCode(node.expression),
                source = NodeSource(node)
            )
        }
    })
}

fun generateCode(node: ExpressionNode): PythonExpressionNode {
    return node.accept(object : ExpressionNode.Visitor<PythonExpressionNode> {
        override fun visit(node: BooleanLiteralNode): PythonExpressionNode {
            return PythonBooleanLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: IntegerLiteralNode): PythonExpressionNode {
            return PythonIntegerLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: StringLiteralNode): PythonExpressionNode {
            return PythonStringLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: VariableReferenceNode): PythonExpressionNode {
            return PythonVariableReferenceNode(node.name, NodeSource(node))
        }

        override fun visit(node: BinaryOperationNode): PythonExpressionNode {
            return PythonBinaryOperationNode(
                operator = generateCode(node.operator),
                left = generateCode(node.left),
                right = generateCode(node.right),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FunctionCallNode): PythonExpressionNode {
            return PythonFunctionCallNode(
                generateCode(node.function),
                node.arguments.map(::generateCode),
                source = NodeSource(node)
            )
        }
    })
}

private fun generateCode(operator: Operator): PythonOperator {
    return when (operator) {
        Operator.EQUALS -> PythonOperator.EQUALS
        Operator.ADD -> PythonOperator.ADD
        Operator.SUBTRACT -> PythonOperator.SUBTRACT
        Operator.MULTIPLY -> PythonOperator.MULTIPLY

    }
}
