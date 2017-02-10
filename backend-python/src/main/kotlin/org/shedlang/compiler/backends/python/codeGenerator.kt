package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.typechecker.VariableReferences

internal fun generateCode(node: ModuleNode, references: VariableReferences): PythonModuleNode {
    return generateCode(node, CodeGenerationContext(references))
}

internal class CodeGenerationContext(
    private val references: VariableReferences,
    private val nodeNames: MutableMap<Int, String> = mutableMapOf(),
    private val namesInScope: MutableSet<String> = mutableSetOf()
) {
    fun name(node: VariableBindingNode): String {
        return name(node.nodeId, node.name)
    }

    fun name(node: ReferenceNode): String {
        val variableNodeId = references[node]
        return name(variableNodeId, node.name)
    }

    private fun name(nodeId: Int, name: String): String {
        if (!nodeNames.containsKey(nodeId)) {
            val pythonName = generateName(name)
            namesInScope.add(pythonName)
            nodeNames[nodeId] = pythonName
        }
        return nodeNames[nodeId]!!
    }

    private fun generateName(originalName: String): String {
        var index = 0
        var name = originalName
        while (namesInScope.contains(name)) {
            index++
            name = originalName + "_" + index
        }
        return name
    }
}

internal fun generateCode(node: ModuleNode, context: CodeGenerationContext): PythonModuleNode {
    return PythonModuleNode(
        node.body.map({ child -> generateCode(child, context) }),
        source = NodeSource(node)
    )
}

internal fun generateCode(node: ModuleStatementNode, context: CodeGenerationContext): PythonStatementNode {
    return node.accept(object : ModuleStatementNode.Visitor<PythonStatementNode> {
        override fun visit(node: ShapeNode): PythonStatementNode {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionNode): PythonStatementNode = generateCode(node, context)
    })
}

internal fun generateCode(node: FunctionNode, context: CodeGenerationContext): PythonFunctionNode {
    return PythonFunctionNode(
        // TODO: test renaming
        name = context.name(node),
        // TODO: test renaming
        arguments = node.arguments.map({ argument -> context.name(argument) }),
        body = generateCode(node.body, context),
        source = NodeSource(node)
    )
}

internal fun generateCode(statements: List<StatementNode>, context: CodeGenerationContext): List<PythonStatementNode> {
    return statements.map({ statement -> generateCode(statement, context) })
}

internal fun generateCode(node: StatementNode, context: CodeGenerationContext): PythonStatementNode {
    return node.accept(object : StatementNode.Visitor<PythonStatementNode> {
        override fun visit(node: ReturnNode): PythonStatementNode {
            return PythonReturnNode(generateCode(node.expression, context), NodeSource(node))
        }

        override fun visit(node: IfStatementNode): PythonStatementNode {
            return PythonIfStatementNode(
                condition = generateCode(node.condition, context),
                trueBranch = generateCode(node.trueBranch, context),
                falseBranch = generateCode(node.falseBranch, context),
                source = NodeSource(node)
            )
        }

        override fun visit(node: ExpressionStatementNode): PythonStatementNode {
            return PythonExpressionStatementNode(generateCode(node.expression, context), NodeSource(node))
        }

        override fun visit(node: ValNode): PythonStatementNode {
            return PythonAssignmentNode(
                name = context.name(node),
                expression = generateCode(node.expression, context),
                source = NodeSource(node)
            )
        }
    })
}

internal fun generateCode(node: ExpressionNode, context: CodeGenerationContext): PythonExpressionNode {
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
            return PythonVariableReferenceNode(context.name(node), NodeSource(node))
        }

        override fun visit(node: BinaryOperationNode): PythonExpressionNode {
            return PythonBinaryOperationNode(
                operator = generateCode(node.operator),
                left = generateCode(node.left, context),
                right = generateCode(node.right, context),
                source = NodeSource(node)
            )
        }

        override fun visit(node: CallNode): PythonExpressionNode {
            return PythonFunctionCallNode(
                generateCode(node.receiver, context),
                node.positionalArguments.map({ argument -> generateCode(argument, context) }),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FieldAccessNode): PythonExpressionNode {
            return PythonAttributeAccessNode(
                generateCode(node.receiver, context),
                node.fieldName,
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
