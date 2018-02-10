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
    fun freshName(): String {
        return generateName("anonymous")
    }

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
        return pythoniseName(originalName)
//        var index = 0
//        var name = generateBaseName(originalName)
//        while (namesInScope.contains(name)) {
//            index++
//            name = originalName + "_" + index
//        }
//        return name
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

    val pythonPackageName = node.path.parts.take(node.path.parts.size - 1)
    val module = when (node.path.base) {
        ImportPathBase.Relative -> "." + pythonPackageName.joinToString(".")
        ImportPathBase.Absolute -> (listOf(topLevelPythonPackageName) + pythonPackageName).joinToString(".")
    }

    val name = node.path.parts.last()

    return PythonImportFromNode(
        module = module,
        names = listOf(name to pythoniseName(name)),
        source = source
    )
}

internal fun generateCode(node: ModuleStatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ShapeNode) = listOf(generateCode(node, context))
        override fun visit(node: UnionNode): List<PythonStatementNode> = listOf()
        override fun visit(node: FunctionDeclarationNode) = listOf(generateCode(node, context))
        override fun visit(node: ValNode) = generateCode(node, context)
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

private fun generateCode(node: FunctionDeclarationNode, context: CodeGenerationContext): PythonFunctionNode {
    return generateFunction(context.name(node), node, context)
}

private fun generateFunction(name: String, node: FunctionNode, context: CodeGenerationContext): PythonFunctionNode {
    return PythonFunctionNode(
        // TODO: test renaming
        name = name,
        // TODO: test renaming
        arguments = generateArguments(node.arguments, context),
        body = generateCode(node.body.statements, context),
        source = NodeSource(node)
    )
}

private fun generateArguments(arguments: List<ArgumentNode>, context: CodeGenerationContext) =
    arguments.map({ argument -> context.name(argument) })

internal fun generateCode(statements: List<StatementNode>, context: CodeGenerationContext): List<PythonStatementNode> {
    return statements.flatMap { statement -> generateCode(statement, context) }
}

internal fun generateCode(node: StatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : StatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ExpressionStatementNode): List<PythonStatementNode> {
            val expression = generateCode(node.expression, context)
            val source = NodeSource(node)
            val statement = if (node.isReturn) {
                PythonReturnNode(expression.value, source)
            } else {
                PythonExpressionStatementNode(expression.value, source)
            }
            return expression.functions + listOf(statement)
        }

        override fun visit(node: ValNode): List<PythonStatementNode> {
            return generateCode(node, context)
        }
    })
}

private fun generateCode(node: ValNode, context: CodeGenerationContext): List<PythonStatementNode> {
    val expression = generateCode(node.expression, context)

    val assignment = PythonAssignmentNode(
        target = PythonVariableReferenceNode(context.name(node), source = NodeSource(node)),
        expression = expression.value,
        source = NodeSource(node)
    )

    return expression.functions + listOf(assignment)
}

internal data class GeneratedCode<T>(
    val value: T,
    val functions: List<PythonFunctionNode>
) {
    fun <R> map(func: (T) -> R) = GeneratedCode(func(value), functions)
    fun <R> flatMap(func: (T) -> GeneratedCode<R>): GeneratedCode<R> {
        val result = func(value)
        return GeneratedCode(
            result.value,
            functions + result.functions
        )
    }

    companion object {
        fun <T> combine(codes: List<GeneratedCode<T>>): GeneratedCode<List<T>> {
            val values = codes.map { code -> code.value }
            val functions = codes.flatMap { code -> code.functions }
            return GeneratedCode(values, functions)
        }
    }
}

private typealias GeneratedExpression = GeneratedCode<PythonExpressionNode>
private typealias GeneratedExpressions = GeneratedCode<List<PythonExpressionNode>>

internal fun generateCode(node: ExpressionNode, context: CodeGenerationContext): GeneratedExpression {
    return node.accept(object : ExpressionNode.Visitor<GeneratedExpression> {
        override fun visit(node: UnitLiteralNode): GeneratedExpression {
            return GeneratedExpression(
                PythonNoneLiteralNode(NodeSource(node)),
                functions = listOf()
            )
        }

        override fun visit(node: BooleanLiteralNode): GeneratedExpression {
            return GeneratedExpression(
                PythonBooleanLiteralNode(node.value, NodeSource(node)),
                functions = listOf()
            )
        }

        override fun visit(node: IntegerLiteralNode): GeneratedExpression {
            return GeneratedExpression(
                PythonIntegerLiteralNode(node.value, NodeSource(node)),
                functions = listOf()
            )
        }

        override fun visit(node: StringLiteralNode): GeneratedExpression {
            return GeneratedExpression(
                PythonStringLiteralNode(node.value, NodeSource(node)),
                functions = listOf()
            )
        }

        override fun visit(node: VariableReferenceNode): GeneratedExpression {
            return GeneratedExpression(
                PythonVariableReferenceNode(context.name(node), NodeSource(node)),
                functions = listOf()
            )
        }

        override fun visit(node: BinaryOperationNode): GeneratedExpression {
            val left = generateCode(node.left, context)
            val right = generateCode(node.right, context)

            return GeneratedExpression(
                PythonBinaryOperationNode(
                    operator = generateCode(node.operator),
                    left = left.value,
                    right = right.value,
                    source = NodeSource(node)
                ),
                functions = left.functions + right.functions
            )
        }

        override fun visit(node: IsNode): GeneratedExpression {
            val expression = generateCode(node.expression, context)

            return GeneratedExpression(
                generateTypeCondition(expression.value, node.type, NodeSource(node)),
                functions = expression.functions
            )
        }

        override fun visit(node: CallNode): GeneratedExpression {
            val receiver = generateCode(node.receiver, context)
            val positionalArguments = generatePositionalArguments(node)
            val namedArguments = generateNamedArguments(node)

            return GeneratedExpression(
                PythonFunctionCallNode(
                    receiver.value,
                    positionalArguments.value,
                    namedArguments.value,
                    source = NodeSource(node)
                ),
                functions = receiver.functions + positionalArguments.functions + namedArguments.functions
            )
        }

        private fun generatePositionalArguments(node: CallNode): GeneratedExpressions {
            val results = node.positionalArguments.map({ argument -> generateCode(argument, context) })
            return GeneratedExpressions(
                value = results.map({ result -> result.value }),
                functions = results.flatMap({ result -> result.functions })
            )
        }

        private fun generateNamedArguments(node: CallNode): GeneratedCode<List<Pair<String, PythonExpressionNode>>> {
            val results = node.namedArguments.map({ argument ->
                argument.name to generateCode(argument.expression, context)
            })
            return GeneratedCode(
                results.map({ (key, value) -> key to value.value }),
                results.flatMap({ (_, value) -> value.functions })
            )
        }

        override fun visit(node: FieldAccessNode): GeneratedExpression {
            val receiver = generateCode(node.receiver, context)

            return GeneratedExpression(
                PythonAttributeAccessNode(
                    receiver.value,
                    pythoniseName(node.fieldName),
                    source = NodeSource(node)
                ),
                functions = receiver.functions
            )
        }

        override fun visit(node: FunctionExpressionNode): GeneratedExpression {
            if (node.body.statements.isEmpty()) {
                return GeneratedExpression(
                    PythonLambdaNode(
                        arguments = generateArguments(node.arguments, context),
                        body = PythonNoneLiteralNode(source = NodeSource(node)),
                        source = NodeSource(node)
                    ),
                    functions = listOf()
                )
            }

            val statement = node.body.statements.singleOrNull()
            if (statement != null && statement is ExpressionStatementNode) {
                val expression = generateCode(statement.expression, context)
                if (expression.functions.isEmpty()) {
                    return GeneratedExpression(
                        PythonLambdaNode(
                            arguments = generateArguments(node.arguments, context),
                            body = expression.value,
                            source = NodeSource(node)
                        ),
                        functions = listOf()
                    )
                }
            }

            val auxiliaryFunction = generateFunction(
                name = context.freshName(),
                node = node,
                context = context
            )

            return GeneratedExpression(
                PythonVariableReferenceNode(auxiliaryFunction.name, source = node.source),
                functions = listOf(auxiliaryFunction)
            )
        }

        override fun visit(node: IfNode): GeneratedExpression {
            return GeneratedCode.combine(node.conditionalBranches.map { branch ->
                generateCode(branch.condition, context).map { condition ->
                    PythonConditionalBranchNode(
                        condition = condition,
                        body = generateCode(branch.body, context),
                        source = NodeSource(branch)
                    )
                }
            }).flatMap { conditionalBranches ->
                val elseBranch = generateCode(node.elseBranch, context)

                generateScopedExpression(
                    body = listOf(
                        PythonIfStatementNode(
                            conditionalBranches = conditionalBranches,
                            elseBranch = elseBranch,
                            source = NodeSource(node)
                        )
                    ),
                    source = NodeSource(node),
                    context = context
                )
            }
        }

        override fun visit(node: WhenNode): GeneratedExpression {
            throw UnsupportedOperationException("not implemented")
        }

        private fun generateTypeCondition(
            expression: PythonExpressionNode,
            type: StaticNode,
            source: Source
        ): PythonFunctionCallNode {
            return PythonFunctionCallNode(
                function = PythonVariableReferenceNode("isinstance", source = source),
                arguments = listOf(expression, generateCode(type, context)),
                keywordArguments = listOf(),
                source = source
            )
        }
    })
}

private fun generateScopedExpression(
    body: List<PythonStatementNode>,
    source: Source,
    context: CodeGenerationContext
): GeneratedExpression {
    val auxiliaryFunction = PythonFunctionNode(
        name = context.freshName(),
        arguments = listOf(),
        body = body,
        source = source
    )
    val callNode: PythonExpressionNode = PythonFunctionCallNode(
        function = PythonVariableReferenceNode(auxiliaryFunction.name, source = source),
        arguments = listOf(),
        keywordArguments = listOf(),
        source = source
    )
    return GeneratedCode(
        callNode,
        functions = listOf(auxiliaryFunction)
    )
}

private fun generateCode(operator: Operator): PythonOperator {
    return when (operator) {
        Operator.EQUALS -> PythonOperator.EQUALS
        Operator.ADD -> PythonOperator.ADD
        Operator.SUBTRACT -> PythonOperator.SUBTRACT
        Operator.MULTIPLY -> PythonOperator.MULTIPLY

    }
}

internal fun generateCode(node: StaticNode, context: CodeGenerationContext): PythonExpressionNode {
    // TODO: test code gen for types
    return node.accept(object : StaticNode.Visitor<PythonExpressionNode> {
        override fun visit(node: StaticReferenceNode): PythonExpressionNode {
            // TODO: test renaming
            return PythonVariableReferenceNode(context.name(node), NodeSource(node))
        }

        override fun visit(node: StaticFieldAccessNode): PythonExpressionNode {
            return PythonAttributeAccessNode(
                generateCode(node.receiver, context),
                pythoniseName(node.fieldName),
                NodeSource(node)
            )
        }

        override fun visit(node: StaticApplicationNode): PythonExpressionNode {
            return generateCode(node.receiver, context)
        }

        override fun visit(node: FunctionTypeNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }
    })
}
