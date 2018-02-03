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
        var index = 0
        var name = generateBaseName(originalName)
        while (namesInScope.contains(name)) {
            index++
            name = originalName + "_" + index
        }
        return name
    }

    private fun isReserved(name: String): Boolean {
        return pythonKeywords.contains(name)
    }

    private fun generateBaseName(originalName: String): String {
        val casedName = if (originalName[0].isUpperCase()) {
            originalName
        } else {
            camelCaseToSnakeCase(originalName)
        }
        return if (isReserved(casedName)) {
            casedName + "_"
        } else {
            casedName
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

    val pythonPackageName = node.path.parts.take(node.path.parts.size - 1)
    val module = when (node.path.base) {
        ImportPathBase.Relative -> "." + pythonPackageName.joinToString(".")
        ImportPathBase.Absolute -> (listOf(topLevelPythonPackageName) + pythonPackageName).joinToString(".")
    }

    return PythonImportFromNode(
        module = module,
        names = listOf(node.path.parts.last()),
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
    return statements.flatMap({ statement -> generateCode(statement, context) })
}

internal fun generateCode(node: StatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : StatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ReturnNode): List<PythonStatementNode> {
            val expression = generateCode(node.expression, context)
            return expression.functions + listOf(PythonReturnNode(expression.value, NodeSource(node)))
        }

        override fun visit(node: IfStatementNode): List<PythonStatementNode> {
            val conditionalBranches = GeneratedCode.combine(node.conditionalBranches.map { branch ->
                generateCode(branch.condition, context).map { condition ->
                    PythonConditionalBranchNode(
                        condition = condition,
                        body = generateCode(branch.body, context),
                        source = NodeSource(branch)
                    )
                }
            })
            val elseBranch = generateCode(node.elseBranch, context)

            val ifStatement = PythonIfStatementNode(
                conditionalBranches = conditionalBranches.value,
                elseBranch = elseBranch,
                source = NodeSource(node)
            )

            return conditionalBranches.functions + listOf(ifStatement)
        }

        override fun visit(node: ExpressionStatementNode): List<PythonStatementNode> {
            val expression = generateCode(node.expression, context)
            val statement = PythonExpressionStatementNode(expression.value, NodeSource(node))
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
                PythonFunctionCallNode(
                    function = PythonVariableReferenceNode("isinstance", source = node.source),
                    arguments = listOf(expression.value, generateCode(node.type, context)),
                    keywordArguments = listOf(),
                    source = node.source
                ),
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
                    node.fieldName,
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
            if (statement != null && statement is ReturnNode) {
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

private fun generateCode(node: StaticNode, context: CodeGenerationContext): PythonExpressionNode {
    // TODO: test code gen for types
    return node.accept(object : StaticNode.Visitor<PythonExpressionNode> {
        override fun visit(node: StaticReferenceNode): PythonExpressionNode {
            // TODO: test renaming
            return PythonVariableReferenceNode(context.name(node), NodeSource(node))
        }

        override fun visit(node: StaticFieldAccessNode): PythonExpressionNode {
            // TODO: test this
            return PythonAttributeAccessNode(generateCode(node.receiver, context), node.fieldName, NodeSource(node))
        }

        override fun visit(node: StaticApplicationNode): PythonExpressionNode {
            return generateCode(node.receiver, context)
        }

        override fun visit(node: FunctionTypeNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

private val pythonKeywords = setOf(
    "False",
    "None",
    "True",
    "and",
    "as",
    "assert",
    "break",
    "class",
    "continue",
    "def",
    "del",
    "elif",
    "else",
    "except",
    "exec",
    "finally",
    "for",
    "from",
    "global",
    "if",
    "import",
    "in",
    "is",
    "lambda",
    "nonlocal",
    "not",
    "or",
    "pass",
    // We add a __future__ statement for print functions, so we don't need to
    // consider print a keyword in any version of Python
    // "print",
    "raise",
    "return",
    "try",
    "while",
    "with",
    "yield"
)
