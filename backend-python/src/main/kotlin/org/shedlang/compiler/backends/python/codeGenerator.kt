package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.ast.*

internal fun generateCode(node: ModuleNode, references: ResolvedReferences): PythonModuleNode {
    return generateCode(node, CodeGenerationContext(references))
}

internal class CodeGenerationContext(
    private val references: ResolvedReferences,
    private val nodeNames: MutableMap<Int, String> = mutableMapOf(),
    private val namesInScope: MutableSet<String> = mutableSetOf()
) {
    fun enterScope(): CodeGenerationContext {
        return CodeGenerationContext(
            references = references,
            nodeNames = nodeNames,
            namesInScope = namesInScope.toMutableSet()
        )
    }

    fun freshName(): String {
        return generateName("anonymous")
    }

    fun name(node: VariableBindingNode): String {
        return name(node.nodeId, node.name)
    }

    fun name(node: ReferenceNode): String {
        return name(references[node])
    }

    private fun name(nodeId: Int, name: String): String {
        if (!nodeNames.containsKey(nodeId)) {
            val pythonName = generateName(name)
            nodeNames[nodeId] = pythonName
        }
        return nodeNames[nodeId]!!
    }

    private fun generateName(originalName: String): String {
        var name = uniquifyName(pythoniseName(originalName))
        namesInScope.add(name)
        return name
    }

    private fun uniquifyName(base: String): String {
        var index = 0
        var name = base
        while (namesInScope.contains(name)) {
            index++
            name = base + "_" + index
        }
        return name
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
        parameters = listOf("self") + node.fields.map({ field -> field.name }),
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
    val bodyContext = context.enterScope()
    return PythonFunctionNode(
        // TODO: test renaming
        name = name,
        // TODO: test renaming
        parameters = generateParameters(node, bodyContext),
        body = generateCode(node.body.statements, bodyContext),
        source = NodeSource(node)
    )
}

private fun generateParameters(function: FunctionNode, context: CodeGenerationContext) =
    function.parameters.map({ parameter -> context.name(parameter) }) +
        function.namedParameters.map({ parameter -> context.name(parameter) })

internal fun generateCode(statements: List<StatementNode>, context: CodeGenerationContext): List<PythonStatementNode> {
    return statements.flatMap { statement -> generateCode(statement, context) }
}

internal fun generateCode(node: StatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : StatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ExpressionStatementNode): List<PythonStatementNode> {
            return generateCode(node, context)
        }

        override fun visit(node: ValNode): List<PythonStatementNode> {
            return generateCode(node, context)
        }
    })
}

private fun generateCode(node: ExpressionStatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return generateCode(node.expression, context).toStatements { expression ->
        val source = NodeSource(node)
        if (node.isReturn) {
            PythonReturnNode(expression, source)
        } else {
            PythonExpressionStatementNode(expression, source)
        }
    }
}

private fun generateCode(node: ValNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return generateCode(node.expression, context).toStatements { expression ->
        PythonAssignmentNode(
            target = PythonVariableReferenceNode(context.name(node), source = NodeSource(node)),
            expression = expression,
            source = NodeSource(node)
        )
    }
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

    fun toStatements(func: (T) -> PythonStatementNode): List<PythonStatementNode> {
        return functions + listOf(func(value))
    }

    fun <R> ifEmpty(func: (T) -> R): R? {
        if (functions.isEmpty()) {
            return func(value)
        } else {
            return null
        }
    }

    companion object {
        fun <T> of(value: T) = GeneratedCode(value, functions = listOf())

        fun <T> flatten(codes: List<GeneratedCode<T>>): GeneratedCode<List<T>> {
            val values = codes.map { code -> code.value }
            val functions = codes.flatMap { code -> code.functions }
            return GeneratedCode(values, functions)
        }

        fun <T1, T2, R> map(
            code1: GeneratedCode<T1>,
            code2: GeneratedCode<T2>,
            func: (T1, T2) -> R
        ): GeneratedCode<R> {
            return GeneratedCode(
                func(code1.value, code2.value),
                functions = code1.functions + code2.functions
            )
        }

        fun <T1, T2, T3, R> map(
            code1: GeneratedCode<T1>,
            code2: GeneratedCode<T2>,
            code3: GeneratedCode<T3>,
            func: (T1, T2, T3) -> R
        ): GeneratedCode<R> {
            return GeneratedCode(
                func(code1.value, code2.value, code3.value),
                functions = code1.functions + code2.functions + code3.functions
            )
        }
    }
}

private typealias GeneratedExpression = GeneratedCode<PythonExpressionNode>
private typealias GeneratedExpressions = GeneratedCode<List<PythonExpressionNode>>

internal fun generateCode(node: ExpressionNode, context: CodeGenerationContext): GeneratedExpression {
    return node.accept(object : ExpressionNode.Visitor<GeneratedExpression> {
        override fun visit(node: UnitLiteralNode): GeneratedExpression {
            return GeneratedExpression.of(
                PythonNoneLiteralNode(NodeSource(node))
            )
        }

        override fun visit(node: BooleanLiteralNode): GeneratedExpression {
            return GeneratedExpression.of(
                PythonBooleanLiteralNode(node.value, NodeSource(node))
            )
        }

        override fun visit(node: IntegerLiteralNode): GeneratedExpression {
            return GeneratedExpression.of(
                PythonIntegerLiteralNode(node.value, NodeSource(node))
            )
        }

        override fun visit(node: StringLiteralNode): GeneratedExpression {
            return GeneratedExpression.of(
                PythonStringLiteralNode(node.value, NodeSource(node))
            )
        }

        override fun visit(node: VariableReferenceNode): GeneratedExpression {
            return GeneratedExpression.of(
                PythonVariableReferenceNode(context.name(node), NodeSource(node))
            )
        }

        override fun visit(node: BinaryOperationNode): GeneratedExpression {
            return GeneratedExpression.map(
                generateCode(node.left, context),
                generateCode(node.right, context),
                { left, right ->
                    PythonBinaryOperationNode(
                        operator = generateCode(node.operator),
                        left = left,
                        right = right,
                        source = NodeSource(node)
                    )
                }
            )
        }

        override fun visit(node: IsNode): GeneratedExpression {
            return generateCode(node.expression, context).map { expression ->
                generateTypeCondition(expression, node.type, NodeSource(node), context)
            }
        }

        override fun visit(node: CallNode): GeneratedExpression {
            return generatedCallCode(node, isPartial = false)
        }

        override fun visit(node: PartialCallNode): GeneratedExpression {
            return generatedCallCode(node, isPartial = true)
        }

        private fun generatedCallCode(node: CallBaseNode, isPartial: Boolean): GeneratedExpression {
            return GeneratedCode.map(
                generateCode(node.receiver, context),
                generatePositionalArguments(node),
                generateNamedArguments(node),
                { receiver, positionalArguments, namedArguments ->
                    val partialArguments = if (isPartial) {
                        // TODO: better handling of builtin
                        listOf(PythonVariableReferenceNode("_partial", source = NodeSource(node)))
                    } else {
                        listOf()
                    }

                    val pythonPositionalArguments = partialArguments + receiver + positionalArguments

                    PythonFunctionCallNode(
                        pythonPositionalArguments.first(),
                        pythonPositionalArguments.drop(1),
                        namedArguments,
                        source = NodeSource(node)
                    )
                }
            )
        }

        private fun generatePositionalArguments(node: CallBaseNode): GeneratedExpressions {
            val results = node.positionalArguments.map({ argument -> generateCode(argument, context) })
            return GeneratedExpressions.flatten(results)
        }

        private fun generateNamedArguments(node: CallBaseNode): GeneratedCode<List<Pair<String, PythonExpressionNode>>> {
            val results = node.namedArguments.map({ argument ->
                generateCode(argument.expression, context).map { expression ->
                    argument.name to expression
                }
            })
            return GeneratedCode.flatten(results)
        }

        override fun visit(node: FieldAccessNode): GeneratedExpression {
            return generateCode(node.receiver, context).map { receiver ->
                PythonAttributeAccessNode(
                    receiver,
                    pythoniseName(node.fieldName),
                    source = NodeSource(node)
                )
            }
        }

        override fun visit(node: FunctionExpressionNode): GeneratedExpression {
            if (node.body.statements.isEmpty()) {
                return GeneratedExpression.of(
                    PythonLambdaNode(
                        parameters = generateParameters(node, context),
                        body = PythonNoneLiteralNode(source = NodeSource(node)),
                        source = NodeSource(node)
                    )
                )
            }

            val statement = node.body.statements.singleOrNull()
            if (statement != null && statement is ExpressionStatementNode) {
                val result = generateCode(statement.expression, context)
                    .ifEmpty { expression -> PythonLambdaNode(
                        parameters = generateParameters(node, context),
                        body = expression,
                        source = NodeSource(node)
                    ) }
                if (result != null) {
                    return GeneratedExpression.of(result)
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
            return GeneratedCode.flatten(node.conditionalBranches.map { branch ->
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
            val temporaryName = context.freshName()
            val expressionCode = generateCode(node.expression, context)
            val branches = node.branches.map { branch ->
                PythonConditionalBranchNode(
                    condition = generateTypeCondition(
                        expression = PythonVariableReferenceNode(
                            name = temporaryName,
                            source = NodeSource(branch)
                        ),
                        type = branch.type,
                        source = NodeSource(branch),
                        context = context
                    ),
                    body = generateCode(branch.body, context),
                    source = NodeSource(branch)
                )
            }
            return expressionCode.flatMap { expression ->
                generateScopedExpression(
                    body = listOf(
                        PythonAssignmentNode(
                            target = PythonVariableReferenceNode(
                                temporaryName,
                                source = NodeSource(node)
                            ),
                            expression = expression,
                            source = NodeSource(node)
                        ),
                        PythonIfStatementNode(
                            conditionalBranches = branches,
                            elseBranch = listOf(),
                            source = NodeSource(node)
                        )
                    ),
                    source = NodeSource(node),
                    context = context
                )
            }
        }
    })
}

private fun generateTypeCondition(
    expression: PythonExpressionNode,
    type: StaticNode,
    source: Source,
    context: CodeGenerationContext
): PythonFunctionCallNode {
    return PythonFunctionCallNode(
        function = PythonVariableReferenceNode("isinstance", source = source),
        arguments = listOf(expression, generateCode(type, context)),
        keywordArguments = listOf(),
        source = source
    )
}

private fun generateScopedExpression(
    body: List<PythonStatementNode>,
    source: Source,
    context: CodeGenerationContext
): GeneratedExpression {
    val auxiliaryFunction = PythonFunctionNode(
        name = context.freshName(),
        parameters = listOf(),
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
