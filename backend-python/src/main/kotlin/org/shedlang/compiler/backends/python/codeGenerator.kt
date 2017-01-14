package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.ast.*

fun generateCode(node: ModuleNode): PythonNode {
    return PythonModuleNode(listOf(), source = NodeSource(node))
}

fun generateCode(node: StatementNode): PythonStatementNode {
    return node.accept(object : StatementNodeVisitor<PythonStatementNode> {
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
    })
}

fun generateCode(node: ExpressionNode): PythonExpressionNode {
    return node.accept(object : ExpressionNodeVisitor<PythonExpressionNode> {
        override fun visit(node: BooleanLiteralNode): PythonExpressionNode {
            return PythonBooleanLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: IntegerLiteralNode): PythonExpressionNode {
            return PythonIntegerLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: VariableReferenceNode): PythonExpressionNode {
            return PythonVariableReferenceNode(node.name, NodeSource(node))
        }

        override fun visit(node: BinaryOperationNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
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
