package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.typechecker.ResolvedReferences

internal fun generateCode(node: ModuleNode, references: ResolvedReferences): PythonModuleNode {
    return generateCode(node, CodeGenerationContext(references))
}

internal class CodeGenerationContext(
    private val references: ResolvedReferences,
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
        var name = generateBaseName(originalName)
        while (namesInScope.contains(name)) {
            index++
            name = originalName + "_" + index
        }
        return name
    }

    private fun generateBaseName(originalName: String): String {
        if (originalName[0].isUpperCase()) {
            return originalName
        } else {
            return camelCaseToSnakeCase(originalName)
        }
    }

    private fun camelCaseToSnakeCase(name: String): String {
        return Regex("\\p{javaUpperCase}").replace(name, { char -> "_" + char.value.toLowerCase() })
    }

}

internal fun generateCode(node: ModuleNode, context: CodeGenerationContext): PythonModuleNode {
    val imports = node.imports.map({ import -> generateCode(import, context) })
    val body = node.body.flatMap({ child -> generateCode(child, context) })
    return PythonModuleNode(
        imports + body,
        source = NodeSource(node)
    )
}

private fun generateCode(node: ImportNode, context: CodeGenerationContext): PythonStatementNode {
    // TODO: assign names properly using context
    val source = NodeSource(node)

    if (node.path.base == ImportPathBase.Relative) {
        return PythonImportFromNode(
            module = "." + node.path.parts.take(node.path.parts.size - 1).joinToString("."),
            names = listOf(node.path.parts.last()),
            source = source
        )
    } else {
        throw UnsupportedOperationException()
    }
}

internal fun generateCode(node: ModuleStatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ShapeNode) = listOf(generateCode(node, context))
        override fun visit(node: UnionNode): List<PythonStatementNode> = listOf()
        override fun visit(node: FunctionNode) = listOf(generateCode(node, context))
    })
}

private fun generateCode(node: ShapeNode, context: CodeGenerationContext): PythonClassNode {
    val init = PythonFunctionNode(
        name = "__init__",
        arguments = listOf("self") + node.fields.map({ field -> field.name }),
        body = node.fields.map({ field ->
            PythonAssignmentNode(
                target = PythonAttributeAccessNode(
                    receiver = PythonVariableReferenceNode("self", source = NodeSource(field)),
                    attributeName = field.name,
                    source = NodeSource(field)
                ),
                expression = PythonVariableReferenceNode(field.name, source = NodeSource(field)),
                source = NodeSource(field)
            )
        }),
        source = NodeSource(node)
    )
    return PythonClassNode(
        // TODO: test renaming
        name = context.name(node),
        body = listOf(init),
        source = NodeSource(node)
    )
}

private fun generateCode(node: FunctionNode, context: CodeGenerationContext): PythonFunctionNode {
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
                target = PythonVariableReferenceNode(context.name(node), source = NodeSource(node)),
                expression = generateCode(node.expression, context),
                source = NodeSource(node)
            )
        }
    })
}

internal fun generateCode(node: ExpressionNode, context: CodeGenerationContext): PythonExpressionNode {
    return node.accept(object : ExpressionNode.Visitor<PythonExpressionNode> {
        override fun visit(node: UnitLiteralNode): PythonExpressionNode {
            return PythonNoneLiteralNode(NodeSource(node))
        }

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

        override fun visit(node: IsNode): PythonExpressionNode {
            return PythonFunctionCallNode(
                function = PythonVariableReferenceNode("isinstance", source = node.source),
                arguments = listOf(generateCode(node.expression, context), generateCode(node.type, context)),
                keywordArguments = mapOf(),
                source = node.source
            )
        }

        override fun visit(node: CallNode): PythonExpressionNode {
            return PythonFunctionCallNode(
                generateCode(node.receiver, context),
                node.positionalArguments.map({ argument -> generateCode(argument, context) }),
                node.namedArguments.associate({ argument ->
                    argument.name to generateCode(argument.expression, context)
                }),
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

private fun generateCode(node: TypeNode, context: CodeGenerationContext): PythonExpressionNode {
    // TODO: test code gen for types
    return node.accept(object : TypeNode.Visitor<PythonExpressionNode> {
        override fun visit(node: TypeReferenceNode): PythonExpressionNode {
            // TODO: test renaming
            return PythonVariableReferenceNode(context.name(node), NodeSource(node))
        }
    })
}
